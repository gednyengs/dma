// =============================================================================
// filename     : package.scala
// description  : AHA DMA Package Object
// author       : Gedeon Nyengele
// =============================================================================

package aha

// Scala Imports
import scala.math._

// Chisel Imports
import chisel3._

package object dma {

    // =========================================================================
    // AXI Parameters
    // =========================================================================

    //
    // Maximum Number of Bytes in an AXI Burst
    //
    val MAX_BURST_BYTES  = 4096

    //
    // Maximum Burst Length Supported in INCR Mode
    //  AXI4 = 256
    //  AXI3 = 16
    //
    val MAX_BURST_LENGTH = 256

    // =========================================================================
    // Type of Command Packet Sent to Reader or Writer
    // =========================================================================
    class CmdBundle(AddrWidth: Int) extends Bundle {
        val NumBytes    = UInt(clog2(MAX_BURST_BYTES).W)
        val Address     = UInt(AddrWidth.W)
    }

    // =========================================================================
    // Transaction Descriptor
    // =========================================================================
    class TransBundle(AddrWidth: Int) extends Bundle {
        val NumBytes    = UInt(32.W)
        val Address     = UInt(AddrWidth.W)
    }

    // =========================================================================
    // CLOG2 Function
    // =========================================================================
    def clog2(x: Int) : Int = {
        require(x >= 0)
        ceil(log(x)/log(2)).toInt
    }

    // =========================================================================
    // LOG2
    // =========================================================================
    def log2(x: Int) : Int = {
        require(x >= 0)
        (log(x)/log(2)).toInt
    }
}
