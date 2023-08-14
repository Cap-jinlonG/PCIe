package PCIe
import chisel3._
import chisel3.util._
import freechips.rocketchip.util._
import scala.collection.immutable.Nil
import freechips.rocketchip.tilelink._
import freechips.rocketchip.tilelink.TLMessages._
import chipsalliance.rocketchip.config.Parameters
import freechips.rocketchip.amba.axi4._
import utility._
import freechips.rocketchip.util._
import freechips.rocketchip.util.MaskGen

class readEntry()  extends Bundle with pcieParameters with AXIBundelParameters {
  val rvalid = Bool()
  val rready = Bool()
  val raddress = UInt(addrBits.W)
  val entryid = UInt(ridBits.W)
  val arid = UInt(aridBits.W)
  val ardata = UInt(entrydatabits.W)
  val readStatus = UInt(2.W)
  val respStatus = UInt(4.W)
  val rsize = UInt(log2Ceil(axidataBits).W)
  val entryFifoId = UInt(fifoBits.W)
  val BeatFifoId = UInt(fifoBits.W)
  val RespFifoId = UInt(fifoBits.W)
  val isFirstBeat =Bool()
  val isLastBeatInSameTrans = Bool()
  val isLastBeatInSameAXI = Bool()
}
class ReadStack(entries : Int = 32) extends Module  with pcieParameters{
  val io = IO(new Bundle(){
    val axiReq_in  = new AXIRequestBundel
    val tlResp_in = new TLRespBundle
    val tlReq_out = new TLRequestBundel
    val axiResp_out = new AXIRespBundle
  })

  def mask(address: UInt, lgSize: UInt): UInt = {
    MaskGen(address, lgSize, beatBytes)
  }
  def isFisrtAXI(arId: UInt,rs:Vec[readEntry]):Bool = !Cat(rs.map(
    m =>
    m.rvalid && m.readStatus === waitSend && m.arid === arId
  )).orR
//  def LastIdx(arId:UInt,rs:Vec[readEntry]): Unit = {
//    rs.zipWithIndex.collectFirst {
//      case(value,index)
//            if (value.rvalid && value.readStatus === waitSend && value.arid === arId && value.isLastBeatInSameAXI === true.B)
//      => index
//    }.getOrElse(-1.U)
//  }
//  def countBeat(arId:UInt,rs:Vec[readEntry]):UInt = (rs.map(e => Mux(e.rvalid && e.arid === arId,true.B,false.B))).reduce(_ +& _)
//

  def countBeat(arId: UInt, rs: Vec[readEntry]): UInt = {
    rs.count(e => e.rvalid && e.arid === arId)
  }

  /*
     Data Structure:
       readStack: 32 entrires
   */
  val readStack = RegInit(VecInit(Seq.fill(entries)(0.U.asTypeOf(new readEntry))))
  val idel::waitSend::waitResp::waitSendResp::done::Nil = Enum(5)
  val axireqArb = Module(new Arbiter(new readEntry,entries))
  val axirespArb = Module(new Arbiter(new readEntry,entries))

  //flags
  val empty = Cat(readStack.map(e => !e.rvalid && e.readStatus === idel)).orR
  val alloc = empty && io.axiReq_in.ar.valid
//  val theLastFifoId = RegInit(0.U)
  println("alloc os :%d\n",alloc)
  io.axiReq_in.ar.ready := empty
  val idxInsert = Mux(alloc,PriorityEncoder(readStack.map(e => !e.rvalid && e.readStatus === idel)),0.U)
//
  //receive AXIReq
  when(alloc)
    {
//      idxInsert = PriorityEncoder(readStack.map(!_.rvalid))
      val entry = readStack(idxInsert)
      val r_size1 =io.axiReq_in.ar.bits.bytes1()
      val r_size = OH1ToUInt(r_size1)
      entry.rvalid := true.B
      entry.entryid := idxInsert
      entry.raddress := io.axiReq_in.ar.bits.addr
      entry.arid := io.axiReq_in.ar.bits.id
      entry.readStatus := 1.U
      entry.rsize := r_size1
//      entry.entryFifoId := readStack.count(e => e.rvalid && (e.readStatus === waitSend))

    //  entry.isFirstBeat := isFisrtAXI(io.axiReq_in.ar.bits.id,readStack)
      print("receive a axi request , entryId:%d arId:%d raddress:%d rsize:%d readStatus:%d entryFifoId:%d beatFifoId:%d\n")

    }


  //issue TLReq
  val hasWaitTLReq = Cat(readStack.map(_.readStatus === waitSend)).orR
  axireqArb.io.in zip readStack foreach{
      case(in,e) =>
      /*
          when entry is valid and is the first beat
       */
      in.valid := e.rvalid && (e.readStatus === waitSend) &&(e.entryFifoId === 0.U)
      in.bits := e
  }
  io.tlReq_out.a.valid := axireqArb.io.out.valid
  io.tlReq_out.a.bits.opcode := TLMessages.Get
  io.tlReq_out.a.bits.param := 0.U
  io.tlReq_out.a.bits.size := axireqArb.io.out.bits.rsize
  io.tlReq_out.a.bits.source := Cat(axireqArb.io.out.bits.entryid,axireqArb.io.out.bits.arid)
  io.tlReq_out.a.bits.address := axireqArb.io.out.bits.raddress
  io.tlReq_out.a.bits.mask := mask(axireqArb.io.out.bits.raddress,axireqArb.io.out.bits.rsize)
  io.tlReq_out.a.bits.data := 0.U
  io.tlReq_out.a.bits.corrupt := false.B
  axireqArb.io.out.ready := io.tlReq_out.a.ready
  val chosen = axireqArb.io.chosen



  //update
  //when tlReq send , entry status will be waitResp
  //when entry is waitSend,entryFIfoId -1
  //chosen entry readStatue:waitSend => waitResp
  when(io.axiReq_in.ar.fire && io.tlReq_out.a.fire)
    {
        val entry = readStack(idxInsert)
        // a entry status will be sending
        entry.entryFifoId := PopCount(Cat(readStack.map(e => e.rvalid && (e.readStatus === waitSend)))) - 1.U
    }.elsewhen(io.axiReq_in.ar.fire && !io.tlReq_out.a.fire)
    {
        val entry = readStack(idxInsert)
        // a entry status will be sending
        entry.entryFifoId := readStack.count(e => e.rvalid && (e.readStatus === waitSend))
    }

  when(io.tlReq_out.a.fire)
  {
    readStack(chosen).readStatus := waitResp
    readStack(chosen).rready := true.B
    for (e <- readStack) {
      when(e.readStatus === waitSend && e.rvalid) {
        e.entryFifoId := e.entryFifoId - 1.U
      }
    }
  }

  //receive TLResq
  val canReceive = Cat(readStack.map(e =>e.rvalid && e.readStatus === waitResp)).orR
  io.tlResp_in.d.ready := canReceive
  when(io.tlResp_in.d.fire)
  {
    val respTLId = io.tlResp_in.d.bits.source
    val respEntryId = respTLId(18, 14).asUInt
    val entryResp = readStack(respEntryId)
    entryResp.ardata := io.tlResp_in.d.bits.data
    entryResp.readStatus := waitSendResp
    entryResp.respStatus := !io.tlResp_in.d.bits.denied //need change
  }

  //issue AXIResp
  val priority = VecInit(readStack.map(e => e.readStatus === waitSendResp && e.rvalid && e.BeatFifoId === 0.U))
  val proVec = priority.zip(readStack).map {
      case(valid,e) =>
        Mux(valid,e.RespFifoId,31.U)
  }
  val max_priority_fifoid = proVec.reduceLeft(_ min _)
  axirespArb.io.in zip readStack foreach{
      case(in,e) =>
      //when entry status is waitSendResp and is the first Beat in same AXI ID
      in.valid := (e.readStatus === waitSendResp && e.rvalid && e.BeatFifoId === 0.U && e.RespFifoId === max_priority_fifoid)
      in.bits := e
  }

  io.axiResp_out.r.valid := axirespArb.io.out.valid
  io.axiResp_out.r.bits.data := axirespArb.io.out.bits.ardata
  io.axiResp_out.r.bits.id := axirespArb.io.out.bits.arid
  io.axiResp_out.r.bits.resp := axirespArb.io.out.bits.respStatus
  val chosenResp = axirespArb.io.chosen
  // need change
//  io.axiResp_out.r.bits.last := ((countBeat(axirespArb.io.out.bits.arid,readStack))===1.U)
  io.axiResp_out.r.bits.last := true.B
  axirespArb.io.out.ready := io.axiResp_out.r.ready

  //clean the entry
  //readStatue : waitResp => done
  //rvalid => false.B
  //beatFifoId - 1

  when( alloc && io.axiResp_out.r.fire)
      {
        val entry = readStack(idxInsert)
        entry.BeatFifoId := PopCount(Cat(readStack.map(e => e.rvalid && (e.arid === io.axiReq_in.ar.bits.id)))) - 1.U
        entry.RespFifoId := PopCount(Cat(readStack.map(e => e.rvalid ))) - 1.U
      }.elsewhen( alloc && !io.axiResp_out.r.fire)
        {
          val entry = readStack(idxInsert)
          entry.BeatFifoId := PopCount(Cat(readStack.map(e => e.rvalid && (e.arid === io.axiReq_in.ar.bits.id))))
          entry.RespFifoId := PopCount(Cat(readStack.map(e => e.rvalid )))
        }
  when(io.axiResp_out.r.fire){
      readStack(chosenResp).rvalid := false.B
      readStack(chosenResp).rready := false.B
      readStack(chosenResp).readStatus := 0.U
      readStack(chosenResp).RespFifoId := 31.U
        for (e <- readStack) {
          when(e.rvalid && e.arid === readStack(chosenResp).arid) {
            e.BeatFifoId := e.BeatFifoId - 1.U
          }
          when(e.rvalid && e.RespFifoId > readStack(chosenResp).RespFifoId)
          {
              e.RespFifoId := e.RespFifoId - 1.U
          }
        }
  }

}


