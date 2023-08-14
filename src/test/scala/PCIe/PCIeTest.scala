package PCIe
import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

object PCIeTest extends  App {
//  emitVerilog(new ReadStack(),Array("--target-dir","generated"))
//  println(getVerilogString(new ReadStack()))
//  emitVerilog(new WriteStack(),Array("--target-dir","generated"))
  emitVerilog(new AXI2TLPCIe(),Array("--target-dir","generated"))
  println(getVerilogString(new AXI2TLPCIe()))
//  "PCIe" should "pass" in(
//        test(new ReadStack){
//            dut =>
//            dut.io.axiReq_in.ar.valid.poke(true.B)
//            dut.io.axiReq_in.ar.bits.id.poke(1.U)
//            dut.io.axiReq_in.ar.bits.addr.poke(0.U)
//            dut.io.axiReq_in.ar.bits.size.poke(1.U)
//            dut.io.axiReq_in.ar.bits.burst.poke(false.B)
//            dut.clock.step()
//        }
//  )
}
