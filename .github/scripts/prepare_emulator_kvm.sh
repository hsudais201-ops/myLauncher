#!/bin/sh
set -eu

if [ ! -e /dev/kvm ]; then
    echo "/dev/kvm is not available on this runner." >&2
    exit 1
fi

sudo chmod 666 /dev/kvm
test -r /dev/kvm
test -w /dev/kvm
echo "KVM access enabled for Android emulator."
