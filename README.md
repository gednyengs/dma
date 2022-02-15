# AHA DMA

Open-Source AXI4 DMA Engine (SystemVerilog and Chisel Versions)

## Features
 - Interrupt
 - AXI4 Support (INCR mode only)
 - 1-D Transfers (Source Address, Destination Address, Length)
 - AXI-Lite and APB Protocol Support for Register File Access

## Chisel Generation

All chisel source code is provided under the `chisel` folder.

To generate the FIRRTL IR and/or SystemVerilog/Verilog views:
- change into the `chisel` folder
- launch `sbt`
- run `run`
- the output files are placed in a subfolder defined in `chisel > src > main > scala > aha > dma > top > Top.scala`

`sbt run` takes command-line arguments. To see the list of command-line arguments supported,
run `sbt run --help`

## Register Space

| Offset | Register Name |
|--------|---------------|
| 0x00      | Control Register (CTRL_REG) |
| 0x04      | Status Register (STAT_REG) |
| 0x08      | Interrupt Clear Register (INT_CLR_REG) |
| 0x0C      | Source Address Register (SRC_ADDR_REG) |
| 0x10      | Destination Address Register (DST_ADDR_REG) |
| 0x18      | Length Register (LEN_REG) |
| 0x1C      | ID Register   (ID_REG)    |

### CTRL_REG

| Field Offset | Field Name | Field Description |
|--------------|------------|-------------------|
| 0 | GO | Write `1` to launch DMA transaction. Write `0` is ignored and reads return `0` |
| 1 | IE | Interrupt enable. Write `1` to enable interrupts, `0` to disable interrupts |
| 31-2 | RESERVED | |

### STAT_REG

This is a Read-Only register (writes are ignored)

| Field Offset | Field Name | Field Description |
|--------------|------------|-------------------|
| 0 | BUSY | Read `1` means DMA is performing a transaction. Read `0` means DMA is idle |
| 1 | INT_STAT | Interrupt status. Read `1` means there is a pending interrupt. Read `0` means no pending interrupts |
| 3-2 | STAT_CODE | Transaction status code (coded like AXI xRESP values) |
| 31 - 4| RESERVED |

### INT_CLR_REG

| Field Offset | Field Name | Field Description |
|--------------|------------|-------------------|
| 0 | RESERVED | |
| 1 | INT_CLR | Write `1` to clear pending interrupt. Writing `0` is ignored and reads always return `0` |
| 31 - 2| RESERVED |


### SRC_ADDR_REG

This is a `R/W` register

| Field Offset | Field Name | Field Description |
|--------------|------------|-------------------|
| 31 - `N` | Source Word Address | Data-bus aligned source address of a transaction. `N` is `log2(DataBusWidth_in_Bytes)` |
| `N-1` - 0 | Ignored address bits | |

### DST_ADDR_REG

This is a `R/W` register

| Field Offset | Field Name | Field Description |
|--------------|------------|-------------------|
| 31 - `N` | Destination Word Address | Data-bus aligned destination address of a transaction. `N` is `log2(DataBusWidth_in_Bytes)` |
| `N-1` - 0 | Ignored address bits | |

### LEN_REG

This is a `R/W` register

| Field Offset | Field Name | Field Description |
|--------------|------------|-------------------|
| 31 - 0 | Length | Length of transaction in bytes |

### ID_REG

This is a Read-Only register (writes are ignored)

| Field Offset | Field Name | Field Description |
|--------------|------------|-------------------|
| 31 - 0 | ID | Value of ID register |


## Programming Sequence

### Interrupt Mode

```c++
// Set Source, Destination, and Length Registers
REG_WRITE(SRC_ADDR_REG, SourceAddr);
REG_WRITE(DST_ADDR_REG, DestAddr);
REG_WRITE(LEN_REG, Length);

// Enable Interrupts and GO
REG_WRITE(CTRL_REG, 0x3);

// After interrupt is received,
// clear interrupt
REG_WRITE(INT_CLR_REG, 0x2)
```

### Non-Interrupt Mode

```c++
// Set Source, Destination, and Length Registers
REG_WRITE(SRC_ADDR_REG, SourceAddr);
REG_WRITE(DST_ADDR_REG, DestAddr);
REG_WRITE(LEN_REG, Length);

// GO
REG_WRITE(CTRL_REG, 0x1);

// Wait for interrupt
while((REG_READ(STAT_REG) & 0x2) == 0);

// clear interrupt
REG_WRITE(INT_CLR_REG, 0x2)
```
