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
     * Name of the type of register file to use
     * (valid values are axi-lite and apb)
     */
    val RegFileName = "apb"

    /**
     * Store-and-Forward FIFO Depth
     * (must be a power of 2)
     */
    val FifoDepth   = 256

    /**
     * ID Register Value
     */
    val MagicID     = 0x5A5A5A5A


    /**
     * Output Subdirecory
     */
    val OutputDir   = "output/"

    // Generate output collateral
    new ChiselStage().emitSystemVerilog(
        new DMA(IdWidth, AddrWidth, DataWidth, FifoDepth, RegFileName, MagicID),
        Array("--target-dir", OutputDir)
    )
} // object Main
