riscv64-unknown-elf-gcc -march=rv32i -mabi=ilp32 -c -o ctest.o ctest.c
riscv64-unknown-elf-ld -b elf32-littleriscv ctest.o -T link.ld -o ctest
riscv64-unknown-elf-objcopy -O binary ctest ctest.bin
od -An -tx1 -w1 -v ctest.bin > ../hex/ctest.hex
riscv64-unknown-elf-objdump -b elf32-littleriscv -D ctest > ../dump/ctest.elf.dmp