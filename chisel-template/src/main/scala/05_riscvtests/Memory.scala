package decode_more

import chisel3._
import chisel3.util._
import chisel3.util.experimental.loadMemoryFromFile
import common.Consts._

// instruction用メモリ
class iMemory extends Bundle{
    // addrをもらってその場所にあるinstを返す
    val addr = Input(UInt(WORD_LEN.W))
    val inst = Output(UInt(WORD_LEN.W))
}

// data用メモリ
class dMemory extends Bundle{
    // read/write用: addrをもらってその場所にあるinstを返す
    val addr = Input(UInt(WORD_LEN.W))
    val data = Output(UInt(WORD_LEN.W))
    // write用: addrとdataもらって書き込むときのenable制御
    val wEn = Input(Bool())
    val wData = Input(UInt(WORD_LEN.W))
}

class Memory extends Module {
    val io = IO(new Bundle{
        val imem = new iMemory()
        val dmem = new dMemory()
    })
    val mem = Mem(16384, UInt(8.W))
    loadMemoryFromFile(mem, "src/hex/sw.hex")

    // addrに対応するinstをメモリから読み取って書き込む
    io.imem.inst := Cat(
        mem(io.imem.addr+3.U(WORD_LEN.W)),
        mem(io.imem.addr+2.U(WORD_LEN.W)),
        mem(io.imem.addr+1.U(WORD_LEN.W)),
        mem(io.imem.addr))
    // addrに対応するdataをメモリから読み取って書き込む
    io.dmem.data := Cat(
        mem(io.dmem.addr+3.U(WORD_LEN.W)),
        mem(io.dmem.addr+2.U(WORD_LEN.W)),
        mem(io.dmem.addr+1.U(WORD_LEN.W)),
        mem(io.dmem.addr))

    // wEn信号がhighならメモリに書き込み
    when(io.dmem.wEn === true.B){
        mem(io.dmem.addr+3.U(WORD_LEN.W)) := io.dmem.wData(31,24)
        mem(io.dmem.addr+2.U(WORD_LEN.W)) := io.dmem.wData(23,16)
        mem(io.dmem.addr+1.U(WORD_LEN.W)) := io.dmem.wData(15,8)
        mem(io.dmem.addr) := io.dmem.wData(7,0)
    }

    printf(p"MEM iaddr : 0x${Hexadecimal(io.imem.addr)}\n")
    printf(p"MEM inst : 0x${Hexadecimal(io.imem.inst)}\n")
    printf(p"MEM daddr : 0x${Hexadecimal(io.dmem.addr)}\n")
    printf(p"MEM data : 0x${Hexadecimal(io.dmem.data)}\n")
}