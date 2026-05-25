cmake_minimum_required(VERSION 3.13)

include(pico_sdk_import.cmake)

project(bitdoglab_pet_lamp_c C CXX ASM)
set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)

pico_sdk_init()

add_executable(bitdoglab_pet_lamp
    main.c
    ws2812.pio
)

pico_generate_pio_header(bitdoglab_pet_lamp ${CMAKE_CURRENT_LIST_DIR}/ws2812.pio)

target_link_libraries(bitdoglab_pet_lamp
    pico_stdlib
    hardware_pio
    hardware_uart
)

pico_enable_stdio_usb(bitdoglab_pet_lamp 0)
pico_enable_stdio_uart(bitdoglab_pet_lamp 0)

pico_add_extra_outputs(bitdoglab_pet_lamp)
