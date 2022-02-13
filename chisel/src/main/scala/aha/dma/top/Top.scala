package aha
package dma

// Chisel Imports
import chisel3._
import chisel3.stage.ChiselStage

// Project Imports
import aha.dma.util._

object Main extends App {

    val IdWidth     = 2
    val AddrWidth   = 32
    val DataWidth   = 64
    val FifoDepth   = 256
    val OutputDir   = "output/"

    val rtl = new ChiselStage().emitSystemVerilog(
                new DMA(IdWidth, AddrWidth, DataWidth, FifoDepth),
                Array("--target-dir", OutputDir)
            )
}
