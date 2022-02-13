# AHA DMA

Open-Source AXI4 DMA Engine (SystemVerilog and Chisel Versions)

## Features
 - Interrupt
 - AXI4 Support (INCR mode only)
 - 1-D Transfers (Source Address, Destination Address, Length)

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

### STAT_REG

### INT_CLR_REG

### SRC_ADDR_REG

### DST_ADDR_REG

### LEN_REG

### ID_REG

## Programming Sequence

### Interrupt Mode

### Non-Interrupt Mode
