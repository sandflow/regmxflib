FROM ubuntu:22.04

# Prevent interactive prompts during package installation
ENV DEBIAN_FRONTEND=noninteractive

# Install development tools, dependencies for asdcplib, and ffmpeg
RUN apt-get update && apt-get install -y \
    build-essential \
    cmake \
    git \
    ffmpeg \
    wget \
    libssl-dev \
    libxerces-c-dev \
    uuid-dev \
    libexpat1-dev \
    liburiparser-dev \
    pkg-config \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /tmp

# Clone, build, and install asdcplib
RUN git clone https://github.com/cinecert/asdcplib.git \
    && cd asdcplib \
    && mkdir build \
    && cd build \
    && cmake -DCMAKE_BUILD_TYPE=Release .. \
    && make \
    && make install \
    && ldconfig \
    && cd /tmp && rm -rf asdcplib

# Clone, build, and install bmx
RUN git clone https://github.com/ebu/bmx.git \
    && cd bmx \
    && mkdir build \
    && cd build \
    && cmake -DCMAKE_BUILD_TYPE=Release .. \
    && make \
    && make install \
    && ldconfig \
    && cd /tmp && rm -rf bmx

# Reset working directory
WORKDIR /root