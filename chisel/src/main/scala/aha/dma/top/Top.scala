package aha
package dma

/* Scala Imports */
import scopt.OParser

/* Chisel Imports */
import chisel3._
import chisel3.stage.ChiselStage

/* Project Imports */
import aha.dma.util._

/**
 * DMA Configuration
 */
case class DMAConfig (
    // Width of AXI ID
    IdWidth: Int        = 2,
    // Width of AXI Address Bus
    AddrWidth: Int      = 32,
    // Width of AXI Data Bus
    DataWidth: Int      = 64,
    // Name of Type of Register File To Use
    // (Supported: axi-lite and apb)
    RegFileName: String = "apb",
    // Depth of Internal Store-and-Forward FIFO
    FifoDepth: Int      = 256,
    // Value of ID Register
    MagicID: Int        = 0x5A5A5A5A,
    // Artifact Output Folder
    OutputDir: String   = "output/"
)


object Main extends App {

    val builder = OParser.builder[DMAConfig]

    val parser = {
        import builder._
        OParser.sequence(
            programName("dma-builder"),
            head("aha-dma", "1.0"),

            opt[Int]('i', "id-width")
                .action((x, c) => c.copy(IdWidth = x))
                .text("width of AXI ID fields"),

            opt[Int]('a', "addr-width")
                .action((x, c) => c.copy(AddrWidth = x))
                .text("width of AXI address busses"),

            opt[Int]('d', "data-width")
                .action((x, c) => c.copy(DataWidth = x))
                .text("width of AXI data busses"),

            opt[String]('r', "reg-file")
                .action((x, c) => c.copy(RegFileName = x))
                .text("name of the type of register file to use (axi-lite or apb)"),

            opt[Int]('f', "fifo-depth")
                .action((x, c) => c.copy(FifoDepth = x))
                .text("depth of internal store-and-forward fifo (must be power of 2)"),

            opt[Int]('m', "magic-id")
                .action((x, c) => c.copy(MagicID = x))
                .text("value of ID register"),

            opt[String]('o', "output-dir")
                .action((x, c) => c.copy(OutputDir = x))
                .text("artifact output directory")
            )
        }

    OParser.parse(parser, args, DMAConfig()) match {
        case Some(config) => new ChiselStage().emitSystemVerilog(
                                new DMA(
                                    config.IdWidth,
                                    config.AddrWidth,
                                    config.DataWidth,
                                    config.FifoDepth,
                                    config.RegFileName,
                                    config.MagicID
                                ),
                                Array("--target-dir", config.OutputDir)
                             )
        case _ => ()
    }
} // object Main
