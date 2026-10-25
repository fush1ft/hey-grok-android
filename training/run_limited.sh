#!/bin/bash
# Run a command niced inside the hgtrain cgroup (memory.max 2600M, cpu.weight 10).
echo $$ | sudo tee /sys/fs/cgroup/hgtrain/cgroup.procs >/dev/null
exec nice -n 19 ionice -c3 "$@"
