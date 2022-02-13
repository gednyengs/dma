// =============================================================================
// filename     : PeekQueue.scala
// description  : Utility Queue with Peek Semantics
// author       : Gedeon Nyengele
// =============================================================================

package aha
package dma
package util

// Chisel Imports
import chisel3._
import chisel3.util.{Decoupled, isPow2}

// Project Imports
import aha.dma.clog2

//
// Peek Queue
//
class PeekQueue[T <: Data](Elem: T, Depth: Int) extends RawModule {

    require((Depth > 0) && isPow2(Depth))

    // =========================================================================
    // I/O
    // =========================================================================

    // Clock and Reset
    val ACLK        = IO(Input(Clock()))
    val ARESETn     = IO(Input(Bool()))

    // Enqueue Interface
    val EnqIntf     = IO(Flipped(Decoupled(Elem)))

    // Dequeue Interface
    val DeqIntf     = IO(Decoupled(Elem))

    // =========================================================================
    // Chisel Work-Around for Active-Low Reset
    // =========================================================================

    val reset       = (!ARESETn).asAsyncReset

    // =========================================================================
    // Internal Logic
    // =========================================================================

    val ADDR_WIDTH  = clog2(Depth);

    val counter     = withClockAndReset(ACLK, reset) { RegInit(0.U((ADDR_WIDTH + 1).W)) }
    val rdptr       = withClockAndReset(ACLK, reset) { RegInit(0.U(ADDR_WIDTH.W)) }
    val wrptr       = withClockAndReset(ACLK, reset) { RegInit(0.U(ADDR_WIDTH.W)) }

    // Internal Memory
    val mem         = Mem(Depth, Elem)

    withClockAndReset(ACLK, reset) {

        //
        // Counter Update Logic
        //
        when (EnqIntf.ready && EnqIntf.valid && DeqIntf.ready && DeqIntf.valid) {
            // Simultaneous Enqueue and Dequeue
            counter     := counter
        }.elsewhen (EnqIntf.ready && EnqIntf.valid) {
            // Enqueue
            counter     := counter + 1.U
        }.elsewhen (DeqIntf.ready && DeqIntf.valid) {
            // Dequeue
            counter     := counter - 1.U
        }

        //
        // Read Pointer Update Logic
        //
        when (DeqIntf.ready && DeqIntf.valid) {
            rdptr       := rdptr + 1.U
        }

        //
        // Write Pointer Update Logic
        //
        when (EnqIntf.ready && EnqIntf.valid) {
            wrptr       := wrptr + 1.U
        }

        //
        // Enqueue Operation (Synchronous)
        //
        when (EnqIntf.ready && EnqIntf.valid) {
            mem(wrptr)  := EnqIntf.bits
        }

        //
        // Read Operation (Asynchronous)
        //
        DeqIntf.bits    := mem(rdptr)

        //
        // Enqueue Ready and Dequeue Valid
        //
        EnqIntf.ready   := counter =/= Depth.U
        DeqIntf.valid   := counter =/= 0.U

    } // withClockAndReset(ACLK, reset_n)
}
