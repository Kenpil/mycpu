package fetch

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
    
    io.exit := (io.imem.inst === 0x34333231.U)

    printf(p"addr: ${Hexadecimal(io.imem.addr)}, memory: ${Hexadecimal(io.imem.inst)}, exit: ${Hexadecimal(io.exit)}\n")
}