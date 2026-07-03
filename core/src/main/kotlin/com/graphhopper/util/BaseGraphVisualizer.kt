package com.graphhopper.util

import com.carrotsearch.hppc.LongHashSet
import com.graphhopper.routing.ev.DecimalEncodedValue
import com.graphhopper.routing.weighting.SpeedWeighting
import com.graphhopper.storage.BaseGraph
import com.graphhopper.storage.NodeAccess
import com.graphhopper.util.shapes.BBox
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.Locale
import java.util.concurrent.CountDownLatch
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.math.max
import kotlin.math.min

class BaseGraphVisualizer private constructor() {

    companion object {
        @JvmStatic
        @JvmOverloads
        fun show(graph: BaseGraph, speedEnc: DecimalEncodedValue? = null) {
            if (graph.nodes > 1000 || graph.edges > 1000)
                throw IllegalArgumentException("BaseGraphVisualizer only supports up to 1000 nodes/edges, "
                        + "but graph has " + graph.nodes + " nodes and " + graph.edges + " edges")
            val latch = CountDownLatch(1)
            SwingUtilities.invokeLater {
                val frame = JFrame("BaseGraph")
                frame.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
                frame.addWindowListener(object : WindowAdapter() {
                    override fun windowClosed(e: WindowEvent) {
                        latch.countDown()
                    }
                })
                frame.add(GraphPanel(graph, speedEnc))
                frame.pack()
                frame.setLocationRelativeTo(null)
                frame.isVisible = true
            }
            try {
                latch.await()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    private class GraphPanel(private val graph: BaseGraph, private val speedEnc: DecimalEncodedValue?) : JPanel() {

        companion object {
            private val L = Locale.US
            private const val PAD = 40
            private const val W = 800
            private const val H = 800
            private val DIST: DistanceCalc = DistanceCalcEuclidean()
            private val LABEL_FONT = Font("SansSerif", Font.PLAIN, 10)
            private val STATS_FONT = Font("SansSerif", Font.PLAIN, 12)
        }

        private val na: NodeAccess = graph.nodeAccess
        private val bounds: BBox
        private val statsText: String
        private var hoveredNode = -1
        private var hoveredEdge = -1
        private var hoveredAdjNode = -1
        private var mouseX = 0
        private var mouseY = 0
        private val clickedEdges = HashSet<Int>()
        private val speedWeighting: SpeedWeighting? = if (speedEnc != null) SpeedWeighting(speedEnc) else null
        private var sumDist = 0L
        private var sumFwdTime = 0L
        private var sumFwdWeight = 0.0

        init {
            preferredSize = Dimension(W, H)
            background = Color(20, 20, 30)
            this.bounds = computeBounds()
            this.statsText = computeStats()
            addMouseMotionListener(object : MouseMotionAdapter() {
                override fun mouseMoved(e: MouseEvent) {
                    mouseX = e.x
                    mouseY = e.y
                    updateHover(mouseX, mouseY)
                }
            })
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.isShiftDown && SwingUtilities.isLeftMouseButton(e) && hoveredEdge >= 0) {
                        if (clickedEdges.add(hoveredEdge)) {
                            val edge = graph.getEdgeIteratorState(hoveredEdge, hoveredAdjNode)!!
                            sumDist += edge.distance_mm
                            if (speedEnc != null) {
                                sumFwdTime += speedWeighting!!.calcEdgeMillis(edge, false)
                                sumFwdWeight += speedWeighting.calcEdgeWeight(edge, false)
                            }
                            repaint()
                        }
                    }
                    if (SwingUtilities.isRightMouseButton(e)) {
                        clickedEdges.clear()
                        sumDist = 0
                        sumFwdTime = 0
                        sumFwdWeight = 0.0
                        repaint()
                    }
                }
            })
        }

        private fun computeStats(): String {
            var stats = "" + graph.nodes + " nodes · " + graph.edges + " edges"
            if (graph.edges > 0) {
                var minDist = Long.MAX_VALUE
                var maxDist = Long.MIN_VALUE
                var minSpeed = Double.MAX_VALUE
                var maxSpeed = Double.MIN_VALUE
                var minTime = Long.MAX_VALUE
                var maxTime = Long.MIN_VALUE
                var minWeight = Double.MAX_VALUE
                var maxWeight = Double.MIN_VALUE
                val iter = graph.allEdges
                while (iter.next()) {
                    val d = iter.distance_mm
                    minDist = min(minDist, d)
                    maxDist = max(maxDist, d)
                    if (speedEnc != null) {
                        val speedFwd = iter.get(speedEnc)
                        val speedBwd = iter.getReverse(speedEnc)
                        minSpeed = min(minSpeed, min(speedFwd, speedBwd))
                        maxSpeed = max(maxSpeed, max(speedFwd, speedBwd))
                        for (reverse in booleanArrayOf(true, false)) {
                            val t = speedWeighting!!.calcEdgeMillis(iter, reverse)
                            if (t < Long.MAX_VALUE) {
                                minTime = min(minTime, t)
                                maxTime = max(maxTime, t)
                            }
                            val w = speedWeighting.calcEdgeWeight(iter, reverse)
                            if (w.isFinite()) {
                                minWeight = min(minWeight, w)
                                maxWeight = max(maxWeight, w)
                            }
                        }
                    }
                }
                stats += String.format(L, " · dist: %d..%dmm", minDist, maxDist)
                if (speedEnc != null) {
                    stats += String.format(L, " · speed: %.0f..%.0fm/s · time: %d..%dms  · weight: %.1f..%.1f", minSpeed, maxSpeed, minTime, maxTime, minWeight, maxWeight)
                }
            }
            return stats
        }

        private fun computeBounds(): BBox {
            val bounds = BBox.createInverse(false)
            for (i in 0 until graph.nodes)
                bounds.update(na.getLat(i), na.getLon(i))
            var padLat = (bounds.maxLat - bounds.minLat) * 0.1
            var padLon = (bounds.maxLon - bounds.minLon) * 0.1
            if (padLat < 1e-9) padLat = 0.001
            if (padLon < 1e-9) padLon = 0.001
            bounds.minLat -= padLat
            bounds.maxLat += padLat
            bounds.minLon -= padLon
            bounds.maxLon += padLon
            return bounds
        }

        private fun sx(lon: Double): Int =
            PAD + ((lon - bounds.minLon) / (bounds.maxLon - bounds.minLon) * (width - 2 * PAD)).toInt()

        private fun sy(lat: Double): Int =
            height - PAD - ((lat - bounds.minLat) / (bounds.maxLat - bounds.minLat) * (height - 2 * PAD)).toInt()

        private fun updateHover(mx: Int, my: Int) {
            val prevNode = hoveredNode
            val prevEdge = hoveredEdge
            hoveredNode = -1
            hoveredEdge = -1
            hoveredAdjNode = -1

            // check nodes first (priority over edges)
            var bestD = 64.0
            for (i in 0 until graph.nodes) {
                val dx = (sx(na.getLon(i)) - mx).toDouble()
                val dy = (sy(na.getLat(i)) - my).toDouble()
                val d = dx * dx + dy * dy
                if (d < bestD) {
                    bestD = d
                    hoveredNode = i
                }
            }

            // check edges only if no node is hovered
            if (hoveredNode == -1) {
                var bestED = 36.0
                val iter = graph.allEdges
                while (iter.next()) {
                    val base = iter.baseNode
                    val adj = iter.adjNode
                    val ax = sx(na.getLon(base)).toDouble()
                    val ay = sy(na.getLat(base)).toDouble()
                    val bx = sx(na.getLon(adj)).toDouble()
                    val by = sy(na.getLat(adj)).toDouble()
                    if (DIST.validEdgeDistance(my.toDouble(), mx.toDouble(), ay, ax, by, bx)) {
                        val d = DIST.calcNormalizedEdgeDistance(my.toDouble(), mx.toDouble(), ay, ax, by, bx)
                        if (d < bestED) {
                            bestED = d
                            hoveredEdge = iter.edge
                            hoveredAdjNode = adj
                        }
                    }
                }
            }
            if (hoveredNode != prevNode || hoveredEdge != prevEdge) repaint()
        }

        private fun drawTooltip(g: Graphics2D, x: Int, y: Int, lines: List<String>) {
            if (lines.isEmpty()) return
            val fm = g.fontMetrics
            val lineH = fm.height
            val pad = 4
            var maxW = 0
            for (line in lines) maxW = max(maxW, fm.stringWidth(line))
            val boxW = maxW + pad * 2
            val boxH = lineH * lines.size + pad * 2
            val bx = x - pad
            val by = y - fm.ascent - pad
            g.color = Color(20, 20, 30, 255)
            g.fillRoundRect(bx, by, boxW, boxH, 6, 6)
            g.color = Color.WHITE
            for (i in lines.indices) {
                g.drawString(lines[i], x, y + i * lineH)
            }
        }

        override fun paintComponent(g0: Graphics) {
            super.paintComponent(g0)
            val g = g0 as Graphics2D
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            // draw edges (duplicate edges between the same node pair get a thicker stroke)
            val seenPairs = LongHashSet()
            val iter = graph.allEdges
            while (iter.next()) {
                val base = iter.baseNode
                val adj = iter.adjNode
                val key = min(base, adj).toLong() shl 32 or max(base, adj).toLong()
                val isDuplicate = !seenPairs.add(key)
                val isClicked = clickedEdges.contains(iter.edge)
                val isHov = iter.edge == hoveredEdge
                if (isClicked) g.color = Color(34, 197, 94)
                else if (isHov) g.color = Color.WHITE
                else g.color = Color(59, 130, 246, 130)
                val width = if (isHov || isClicked) 2f else if (isDuplicate) 4f else 1f
                g.stroke = BasicStroke(width)
                g.drawLine(sx(na.getLon(base)), sy(na.getLat(base)), sx(na.getLon(adj)), sy(na.getLat(adj)))
            }

            // draw nodes
            g.stroke = BasicStroke(1f)
            g.font = LABEL_FONT
            for (i in 0 until graph.nodes) {
                val isHov = i == hoveredNode
                val r = if (isHov) 6 else 3
                val px = sx(na.getLon(i))
                val py = sy(na.getLat(i))
                g.color = if (isHov) Color.WHITE else Color(229, 229, 229)
                g.fillOval(px - r, py - r, r * 2, r * 2)
                g.color = Color(160, 160, 180)
                g.drawString(i.toString(), px + 8, py - 6)
            }

            // stats
            g.font = STATS_FONT
            g.color = Color(160, 160, 180)
            g.drawString(statsText, 10, height - 10)

            // hover info (next to cursor)
            g.font = LABEL_FONT
            val tx = mouseX + 15
            val ty = mouseY - 10
            if (hoveredEdge >= 0) {
                val e = graph.getEdgeIteratorState(hoveredEdge, hoveredAdjNode)!!
                val lines = ArrayList<String>()
                lines.add(String.format(L, "Edge %d: %d -> %d", e.edge, e.baseNode, e.adjNode))
                lines.add(String.format(L, "Distance: %dmm", e.distance_mm))
                if (speedEnc != null) {
                    val fwdSpeed = e.get(speedEnc)
                    val bwdSpeed = e.getReverse(speedEnc)
                    lines.add(String.format(L, "Speed-Fwd: %.1f m/s (%.0f km/h)", fwdSpeed, fwdSpeed * 3.6))
                    lines.add(String.format(L, "Speed-Bwd: %.1f m/s (%.0f km/h)", bwdSpeed, bwdSpeed * 3.6))
                    val fwdTime = speedWeighting!!.calcEdgeMillis(e, false)
                    val bwdTime = speedWeighting.calcEdgeMillis(e, true)
                    lines.add(String.format(L, "Time-Fwd: %dms", fwdTime))
                    lines.add(String.format(L, "Time-Bwd: %dms", bwdTime))
                    val fwdWeight = speedWeighting.calcEdgeWeight(e, false)
                    val bwdWeight = speedWeighting.calcEdgeWeight(e, true)
                    lines.add(String.format(L, "Weight-Fwd: %.3f", fwdWeight))
                    lines.add(String.format(L, "Weight-Bwd: %.3f", bwdWeight))
                }
                drawTooltip(g, tx, ty, lines)
            }
            if (hoveredNode >= 0) {
                val lines = ArrayList<String>()
                lines.add(String.format(L, "Node %d", hoveredNode))
                lines.add(String.format(L, "Lat: %.6f", na.getLat(hoveredNode)))
                lines.add(String.format(L, "Lon: %.6f", na.getLon(hoveredNode)))
                drawTooltip(g, tx, ty, lines)
            }

            // sum of clicked edges (top-right)
            if (!clickedEdges.isEmpty()) {
                g.font = LABEL_FONT
                g.color = Color(34, 197, 94)
                var sumInfo = String.format(L, "Sum (%d edges): Distance=%dmm", clickedEdges.size, sumDist)
                if (speedEnc != null) {
                    sumInfo += String.format(L, ", Time-Fwd=%dms", sumFwdTime)
                    sumInfo += String.format(L, ", Weight-Fwd=%.3f", sumFwdWeight)
                }
                val fm = g.fontMetrics
                g.drawString(sumInfo, width - fm.stringWidth(sumInfo) - 10, 20)
            }

            // instructions (bottom-right)
            g.font = LABEL_FONT
            g.color = Color(100, 100, 120)
            val instructions = "shift+click: sum edges | right-click: reset"
            val fm = g.fontMetrics
            g.drawString(instructions, width - fm.stringWidth(instructions) - 10, height - 10)
        }
    }
}
