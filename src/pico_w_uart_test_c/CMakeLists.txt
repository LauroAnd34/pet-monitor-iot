cmake_minimum_required(VERSION 3.13)

include(pico_sdk_import.cmake)

project(pico_uart_test C CXX ASM)
set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)

pico_sdk_init()

add_executable(pico_uart_test
    main.c
)

target_link_libraries(pico_uart_test
    pico_stdlib
    hardware_uart
)

pico_enable_stdio_usb(pico_uart_test 1)
pico_enable_stdio_uart(pico_uart_test 0)

pico_add_bin_output(pico_uart_test)
pico_add_hex_output(pico_uart_test)
pico_add_uf2_output(pico_uart_test)
