package aha
package dma
package util

/* Chisel Imports */
import chisel3._

/**
 * AMBA 3 APB Protocol v1.0 Interface
 *
 * @constructor         constructs an APB interface bundle with the provided
 *                      address bus width and data bus width
 * @param AddrWidth     the width of the APB address bus (PADDR)
 * @param DataWidth     the width of the APB data busses (PWDATA and PRDATA)
 */
class APBIntf(AddrWidth: Int, DataWidth: Int) extends Bundle {
    val PADDR       = Output(UInt(AddrWidth.W))
    val PSEL        = Output(Bool())
    val PENABLE     = Output(Bool())
    val PWRITE      = Output(Bool())
    val PWDATA      = Output(UInt(DataWidth.W))
    val PREADY      = Input(Bool())
    val PRDATA      = Input(UInt(DataWidth.W))
    val PSLVERR     = Input(Bool())
}
