#!/bin/bash

# Ensure we have a file argument
if [ $# -eq 0 ]; then
    echo "Usage: $0 <input_shader_file>"
    exit 1
fi

# Paths to the tools
matc="/Users/erleb/Documents/repos/filament/cmake-build-debug/tools/matc/matc"
glslc="/Users/erleb/Documents/repos/shaderc/cmake-build-debug/glslc/glslc"
tint="/Users/erleb/Documents/repos/dawn/cmake-build-debug/tint"

# The input shader file
input_file="$1"

# Run matc to generate the shader files
"$matc" -p desktop -o test -R -a vulkan  "$input_file"

# Create necessary subdirectories if they don't exist
mkdir -p vert frag spv wgsl

# Move vert and frag files into their respective directories
for shader in *.vert; do
    if [ -f "$shader" ]; then
        mv "$shader" vert/
    fi
done

for shader in *.frag; do
    if [ -f "$shader" ]; then
        mv "$shader" frag/
    fi
done

# Process the vert files
for vert_file in vert/*.vert; do
    if [ -f "$vert_file" ]; then
        # Run glslc on the vert file
        "$glslc" -g -O -o "spv/$(basename "$vert_file").spv" "$vert_file"
    fi
done

# Process the frag files
for frag_file in frag/*.frag; do
    if [ -f "$frag_file" ]; then
        # Run glslc on the frag file
        "$glslc" -O -o "spv/$(basename "$frag_file").spv" "$frag_file"
    fi
done

# Process the .spv files using tint
for spv_file in spv/*.spv; do
    if [ -f "$spv_file" ]; then
        # Extract the file name without the path and extension
        file_name=$(basename "$spv_file" .spv)
        
        # Run tint and capture stdout and stderr separately
        tint_output=$(mktemp)
        tint_error=$(mktemp)
        
        # Run tint and capture stdout to tint_output and stderr to tint_error
        "$tint" -o "wgsl/$file_name.wgsl" "$spv_file" > "$tint_output" 2> "$tint_error"
        
        # If there was any error, append both stdout and stderr to the log file
        if [ -s "$tint_error" ]; then
            {
                # Append stdout first
                cat "$tint_output"
                # Then append stderr
                cat "$tint_error"
            } > "wgsl/error.$file_name.log"
            
            echo "Error processing $spv_file. Check wgsl/error.$file_name.log for details."
        fi
        
        # Clean up temporary files
        rm "$tint_output" "$tint_error"
    fi
done

echo "Processing complete."
