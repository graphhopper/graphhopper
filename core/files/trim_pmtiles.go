package main

import (
	"bytes"
	"fmt"
	"io"
	"log"
	"os"
	"sort"
	"strconv"

	"github.com/protomaps/go-pmtiles/pmtiles"
)

type tileKey struct {
	off uint64
	len uint32
}

func must(err error) {
	if err != nil {
		log.Fatal(err)
	}
}

func copyRange(src, dst *os.File, off uint64, n uint32) error {
	r := io.NewSectionReader(src, int64(off), int64(n))
	_, err := io.Copy(dst, r)
	return err
}

func main() {
	if len(os.Args) != 4 {
		log.Fatalf("usage: %s <input.pmtiles> <output.pmtiles> <maxzoom>", os.Args[0])
	}
	inPath := os.Args[1]
	outPath := os.Args[2]

	mz, err := strconv.Atoi(os.Args[3])
	must(err)
	if mz < 0 || mz > 31 {
		log.Fatal("maxzoom must be 0..31")
	}
	maxZoom := uint8(mz)

	in, err := os.Open(inPath)
	must(err)
	defer in.Close()

	hbuf := make([]byte, pmtiles.HeaderV3LenBytes)
	_, err = io.ReadFull(in, hbuf)
	must(err)

	hdr, err := pmtiles.DeserializeHeader(hbuf)
	must(err)
	if !hdr.Clustered {
		log.Fatal("input must be clustered")
	}

	metaRaw := make([]byte, hdr.MetadataLength)
	_, err = in.ReadAt(metaRaw, int64(hdr.MetadataOffset))
	must(err)

	// Best-effort metadata maxzoom update.
	if meta, err := pmtiles.DeserializeMetadata(bytes.NewReader(metaRaw), hdr.InternalCompression); err == nil {
		meta["maxzoom"] = int(maxZoom)
		if b, err := pmtiles.SerializeMetadata(meta, hdr.InternalCompression); err == nil {
			metaRaw = b
		}
	}

	var (
		entries   []pmtiles.EntryV3
		addressed uint64
	)

	// Keep unique source tile ranges and remap them in a second pass.
	seen := make(map[tileKey]struct{}, 1<<20)
	ranges := make([]tileKey, 0, 1<<20)

	// Exclusive upper bound: first tile ID at (maxZoom + 1).
	maxTileID := pmtiles.ZxyToID(maxZoom+1, 0, 0)

	fetch := func(offset, length uint64) ([]byte, error) {
		b := make([]byte, length)
		_, err := in.ReadAt(b, int64(offset))
		return b, err
	}

	var opErr error
	op := func(e pmtiles.EntryV3) {
		if opErr != nil {
			return
		}
		if e.TileID >= maxTileID || e.Length == 0 {
			return
		}

		rl := uint64(e.RunLength)
		if rl == 0 {
			rl = 1
		}
		if e.TileID+rl > maxTileID {
			rl = maxTileID - e.TileID
		}
		if rl == 0 {
			return
		}
		e.RunLength = uint32(rl)

		// Most PMTiles use offset relative to TileDataOffset.
		// Some producer implementations may write absolute offsets.
		relOff := e.Offset
		if relOff+uint64(e.Length) > hdr.TileDataLength {
			if e.Offset >= hdr.TileDataOffset &&
				e.Offset+uint64(e.Length) <= hdr.TileDataOffset+hdr.TileDataLength {
				relOff = e.Offset - hdr.TileDataOffset
			} else {
				opErr = fmt.Errorf("invalid tile range: off=%d len=%d tileDataLen=%d tileDataOff=%d", e.Offset, e.Length, hdr.TileDataLength, hdr.TileDataOffset)
				return
			}
		}
		e.Offset = relOff

		k := tileKey{off: e.Offset, len: e.Length}
		if _, ok := seen[k]; !ok {
			seen[k] = struct{}{}
			ranges = append(ranges, k)
		}

		entries = append(entries, e)
		addressed += uint64(e.RunLength)
	}

	err = pmtiles.IterateEntries(hdr, fetch, op)
	must(err)
	must(opErr)

	sort.Slice(ranges, func(i, j int) bool {
		if ranges[i].off == ranges[j].off {
			return ranges[i].len < ranges[j].len
		}
		return ranges[i].off < ranges[j].off
	})

	remap := make(map[tileKey]uint64, len(ranges))
	var tileBytes uint64
	for _, r := range ranges {
		remap[r] = tileBytes
		tileBytes += uint64(r.len)
	}
	for i := range entries {
		k := tileKey{off: entries[i].Offset, len: entries[i].Length}
		entries[i].Offset = remap[k]
	}

	rootBytes, leafBytes, _ := pmtiles.BuildDirectories(entries, int(hdr.RootLength), hdr.InternalCompression)

	outHdr := hdr
	outHdr.MaxZoom = maxZoom
	if outHdr.CenterZoom > maxZoom {
		outHdr.CenterZoom = maxZoom
	}
	outHdr.AddressedTilesCount = addressed
	outHdr.TileEntriesCount = uint64(len(entries))
	outHdr.TileContentsCount = uint64(len(ranges))

	outHdr.RootOffset = pmtiles.HeaderV3LenBytes
	outHdr.RootLength = uint64(len(rootBytes))
	outHdr.MetadataOffset = outHdr.RootOffset + outHdr.RootLength
	outHdr.MetadataLength = uint64(len(metaRaw))
	outHdr.LeafDirectoryOffset = outHdr.MetadataOffset + outHdr.MetadataLength
	outHdr.LeafDirectoryLength = uint64(len(leafBytes))
	outHdr.TileDataOffset = outHdr.LeafDirectoryOffset + outHdr.LeafDirectoryLength
	outHdr.TileDataLength = tileBytes

	out, err := os.Create(outPath)
	must(err)
	defer out.Close()

	_, err = out.Write(pmtiles.SerializeHeader(outHdr))
	must(err)
	_, err = out.Write(rootBytes)
	must(err)
	_, err = out.Write(metaRaw)
	must(err)
	_, err = out.Write(leafBytes)
	must(err)

	for _, r := range ranges {
		err := copyRange(in, out, hdr.TileDataOffset+r.off, r.len)
		must(err)
	}

	fmt.Printf("wrote %s (maxzoom=%d, entries=%d, unique_ranges=%d, tiledata=%d bytes)\n", outPath, maxZoom, len(entries), len(ranges), tileBytes)
}
