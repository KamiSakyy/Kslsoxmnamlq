cmake_minimum_required(VERSION 3.22.1)
project("tsuyu_sec")

add_library(
    tsuyu_sec
    SHARED
    tsuyu_sec.c
)

find_library(
    log-lib
    log
)

target_link_libraries(
    tsuyu_sec
    ${log-lib}
)
