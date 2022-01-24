package tutorialHello

import chisel3._
import chisel3.util._
import chisel3.util.experimental.loadMemoryFromFile

class Ios extends Bundle{
    val x = Input(UInt(32.W))
}

class Hello extends Module{
    val Inputs = RegInit(0.U(32.W))
    // Bundle系は「io」の名前の中でしか宣言できない
    val io = IO(new Bundle{
        val exit = Output(Bool())
        // InputをフリップしてOutputにする
        val Outputs = Flipped(new Ios())
    })
    io.Outputs.x := Inputs + 1.U(32.W)
    io.exit := (Inputs === 0x100.U(32.W))

    // 最大16384アドレス
    val mem = Mem(16384, UInt(32.W))
    loadMemoryFromFile(mem, "src/hex/hello.hex")

    val addr = RegInit(0.U(32.W))
    addr := addr + 1.U(32.W)

    Inputs := mem(addr)

    printf("Hello World !\n")
    printf(p"in : ${Hexadecimal(Inputs)}, addr: ${Hexadecimal(addr)}, exit ${Hexadecimal(io.exit)}\n")
    printf(p"out : ${Hexadecimal(io.Outputs.x)}\n")
}