package com.graphhopper.routing.matrix;

import com.graphhopper.GraphHopper;
import com.graphhopper.config.Profile;
import com.graphhopper.storage.DataAccess;
import com.graphhopper.util.Constants;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/** A content identity of the loaded immutable import, calculated once per engine, never per cell. */
final class MatrixProvenance {
    final String graphFingerprint;
    final String buildCommit;
    private final Map<String, String> profileFingerprints = new TreeMap<>();

    MatrixProvenance(GraphHopper hopper) {
        buildCommit = Constants.GIT_INFO == null ? "unknown-build" :
                Constants.GIT_INFO.getCommitHash() + (Constants.GIT_INFO.isDirty() ? "+dirty" : "");
        MessageDigest digest = digest();
        text(digest, "local-matrix-graph-v1");
        text(digest, Constants.VERSION);
        text(digest, buildCommit);
        text(digest, Constants.getVersions());
        text(digest, hopper.getEncodingManager().toString());
        text(digest, new TreeMap<>(hopper.getProperties().getAll()).toString());
        // Hash the actual loaded storage, including geometry, encoded values, turn restrictions and index.
        // This also works for RAM imports and does not depend on file paths, timestamps, or a retained PBF.
        for (Map.Entry<String, DataAccess> entry : new TreeMap<>(hopper.getBaseGraph().getDirectory().getDAs()).entrySet()) {
            DataAccess data = entry.getValue();
            if (data.isClosed())
                continue;
            text(digest, entry.getKey());
            text(digest, Long.toString(data.getCapacity()));
            ByteBuffer header = ByteBuffer.allocate(80).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < 80; i += 4)
                header.putInt(data.getHeader(i));
            digest.update(header.array());
            byte[] bytes = new byte[Math.min(65536, data.getSegmentSize())];
            for (long offset = 0; offset < data.getCapacity(); offset += bytes.length) {
                int length = (int) Math.min(bytes.length, data.getCapacity() - offset);
                if (data.getType().isInteg()) {
                    ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
                    for (int i = 0; i < length; i += 4)
                        buffer.putInt(data.getInt(offset + i));
                } else {
                    data.getBytes(offset, bytes, length);
                }
                digest.update(bytes, 0, length);
            }
        }
        graphFingerprint = HexFormat.of().formatHex(digest.digest());
        for (Profile profile : hopper.getProfiles()) {
            MessageDigest profileDigest = digest();
            text(profileDigest, graphFingerprint);
            text(profileDigest, Constants.VERSION);
            text(profileDigest, profile.toString());
            profileFingerprints.put(profile.getName(), HexFormat.of().formatHex(profileDigest.digest()));
        }
    }

    String profileFingerprint(String profile) {
        return profileFingerprints.get(profile);
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void text(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array());
        digest.update(bytes);
    }
}
