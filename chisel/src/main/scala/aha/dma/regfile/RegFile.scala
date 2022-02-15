package aha
package dma

/* Chisel Imports */
import chisel3._

/**
 * Configuration of a Register File
 *
 * @constructor         constructs a configuration object for a register file
 * @param AddrWidth     the width of the address bus on the register file interface
 * @param DataWidth     the width of the data bus on the register file interface
 * @param MagicID       the value of the ID register in the registe file
 */
case class RegFileConfig(AddrWidth: Int, DataWidth: Int, MagicID: Int)

/**
 * RegFile Trait
 */
trait RegFile extends RawModule {

    // Clock and Reset
    val ACLK            = IO(Input(Clock()))
    val ARESETn         = IO(Input(Bool()))

    // Control Signals
    val GO_Pulse        = IO(Output(Bool()))
    val IRQEnable       = IO(Output(Bool()))
    val IRQClear_Pulse  = IO(Output(Bool()))
    val SrcAddr         = IO(Output(UInt(32.W)))
    val DstAddr         = IO(Output(UInt(32.W)))
    val Length          = IO(Output(UInt(32.W)))

    // Status Signals
    val Busy            = IO(Input(Bool()))
    val IRQStatus       = IO(Input(Bool()))
    val StatCode        = IO(Input(UInt(2.W)))

    // Register File Interface
    lazy val RegIntf    = IO(Flipped(getRegFileIntf))

    // Type  of Register File Interface
    type RegIntfTy <: Bundle

    // Register File Interface
    def getRegFileIntf : RegIntfTy

} // trait RegFile

/**
 * RegFile Factory
 */
object RegFile {

    /**
     * Build a register file based on the name and configuration provided
     */
    @throws(classOf[RuntimeException])
    def get(name: String, config: RegFileConfig) : RegFile  = {

        if ((config.AddrWidth != 32) || (config.DataWidth != 32)) {
            throw new RuntimeException(
                """register interfaces supports only address bus width
                of 32 and data bus width of 32""")
        } else {

            name.toLowerCase() match {

                // AXI Lite
                case "axilite" | "axi-lite" | "axi_lite" => new AXILiteRegFile(config.MagicID)

                // APB
                case "apb" => new APBRegFile(config.MagicID)

                // Unsupported
                case x => throw new RuntimeException(s"unsupported register file type $x")
            }

        }
    }
}
