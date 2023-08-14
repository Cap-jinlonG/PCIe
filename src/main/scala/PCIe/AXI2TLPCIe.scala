package PCIe

import chisel3._
import chisel3.util._
import freechips.rocketchip.amba.axi4.{AXI4BundleAR, AXI4BundleAW, AXI4BundleB, AXI4BundleParameters, AXI4BundleR, AXI4BundleW}
import freechips.rocketchip.tilelink.{TLBundleA, TLBundleD, TLBundleParameters}


class AXI2TLPCIe() extends Module with pcieParameters {
      val io = IO(new Bundle() {
        val in = new PEIeInBundle()
        val out = new PCIeOutBundle()
      })
      val readStack = Module(new ReadStack(entries=32))
      val writeStack = Module(new WriteStack(entries=32))
      //in
      readStack.io.axiReq_in.ar <> io.in.ar
      readStack.io.tlResp_in.d <> io.in.d
      writeStack.io.axiWriteReq_in.aw <> io.in.aw
      writeStack.io.axiWriteReq_in.w <> io.in.w
      writeStack.io.axiMenWriteResp_in.b <> io.in.b
      writeStack.io.validResp_out <> io.in.validResp_out

      //out
      io.out.a <> readStack.io.tlReq_out.a
      io.out.validReq_in <> writeStack.io.validReq_in
      io.out.aw <> writeStack.io.axiMenWriteReq_out.aw
      io.out.w <> writeStack.io.axiMenWriteReq_out.w
      io.out.r <> readStack.io.axiResp_out.r
      io.out.b <> writeStack.io.axiWriteResp_out.b

}
