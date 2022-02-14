package aha

/* Scala Imports */
import scala.math._

/* Chisel Imports */
import chisel3._

/**
 * Provides project-wide constants, transfer and transaction bundle types, and
 * some utility functions
 */
package object dma {

    // =========================================================================
    // AXI Parameters
    // =========================================================================

    /**
     * Maximum Number of Bytes in an AXI Burst
     */
    val MAX_BURST_BYTES  = 4096

    /**
     * Maximum Burst Length Supported in INCR Mode
     *  AXI4 = 256
     *  AXI3 = 16
     */
    val MAX_BURST_LENGTH = 256

    /**
     * Transfer command bundle
     *
     * @constructor     constructs a transfer command bundle with a specified
     *                  AXI address bus width
     * @param AddrWidth the width of AXI address busses (AWADDR and ARADDR)
     */
    class CmdBundle(AddrWidth: Int) extends Bundle {
        val NumBytes    = UInt(clog2(MAX_BURST_BYTES).W)
        val Address     = UInt(AddrWidth.W)
    }

    /**
     * Transaction command bundle
     *
     * @constructor     constructs a transaction command bundle with a specified
     *                  AXI address bus width
     * @param AddrWidth the width of AXI address busses (AWADDR and ARADDR)
     */
    class TransBundle(AddrWidth: Int) extends Bundle {
        val NumBytes    = UInt(32.W)
        val Address     = UInt(AddrWidth.W)
    }

    /**
     * Returns log2 of a number rounded up
     *
     * @param x the number of which the clog2 is computed
     * @return the log2 of x rounded up
     */
    def clog2(x: Int) : Int = {
        require(x >= 0)
        ceil(log(x)/log(2)).toInt
    }

    /**
     * Returns log2 of a number
     *
     * @param x the number of which the log2 is computed
     * @return the log2 of x
     */
    def log2(x: Int) : Int = {
        require(x >= 0)
        (log(x)/log(2)).toInt
    }
}
