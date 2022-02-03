package decode

import chisel3._
import common.Consts._

class Core extends Module {
    val io = IO(new Bundle{
        val imem = Flipped(new memoryIo())
        val exit = Output(Bool())
    })
    val pc = RegInit(0.U(WORD_LEN.W))
    // pcを4ずつカウントアップ
    pc := pc + 4.U(WORD_LEN.W)
    io.imem.addr := pc

    val regFile = Mem(32, UInt(WORD_LEN.W))

    // 配線
    val rs1Addr = io.imem.inst(19,15)
    val rs2Addr = io.imem.inst(24,20)
    val rdAddr = io.imem.inst(11,7)
    // アドレスが非ゼロならレジスタから読み取り，ゼロなら何もせん
    val rs1Data = Mux(rs1Addr =/= 0.U(WORD_LEN.W), regFile(rs1Addr), 0.U(WORD_LEN.W))
    val rs2Data = Mux(rs1Addr =/= 0.U(WORD_LEN.W), regFile(rs2Addr), 0.U(WORD_LEN.W))
  
  //**********************************
  // Debug
  io.exit := (io.imem.inst === 0x34333231.U(WORD_LEN.W))
  printf(p"pc   : 0x${Hexadecimal(pc)}\n")
  printf(p"inst     : 0x${Hexadecimal(io.imem.inst)}\n")
  printf(p"rs1_addr : $rs1Addr\n")
  printf(p"rs2_addr : $rs2Addr\n")
  printf(p"rd_addr  : $rdAddr\n")
  printf(p"rs1_data : 0x${Hexadecimal(rs1Data)}\n")
  printf(p"rs2_data : 0x${Hexadecimal(rs2Data)}\n")
  printf("---------\n")

}