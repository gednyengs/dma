package aha
package dma

/* Chisel Imports */
import chisel3._
import chisel3.stage.ChiselStage

/* Project Imports */
import aha.dma.util._

object Main extends App {

    /**
     * AXI ID width
     */
    val IdWidth     = 2

    /**
     * AXI Address Bus Width
     */
    val AddrWidth   = 32

    /**
     * AXI Data Bus width
     */
    val DataWidth   = 64

    /**
     * Store-and-Forward FIFO Depth
     * (must be a power of 2)
     */
    val FifoDepth   = 256

    /**
     * Output Subdirecory
     */
    val OutputDir   = "output/"

    // Generate output collateral
    new ChiselStage().emitSystemVerilog(
        new DMA(IdWidth, AddrWidth, DataWidth, FifoDepth),
        Array("--target-dir", OutputDir)
    )
} // object Main
