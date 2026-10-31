<!-- Project wording; the content below is intentionally separate from private inventory evidence. -->

## title
HELM VM Image Tooling

## subtitle
Reproducible boot artifacts for an ARM Linux guest.

## status
Infrastructure prototype

## year
2026

## role
Guest-image build and packaging tooling.

## summary
HELM VM Image Tooling builds an ARM Linux guest filesystem and packages the kernel, initial RAM filesystem and compressed root image. A checksum manifest records the generated artifacts. A successful guest boot has not been verified.

## facts
- Phase: Infrastructure prototype
- Stack: Shell · Arch Linux ARM · ext4 · zstd · SHA-256
- Review: Source inspected · 31 October 2026

## highlights
- Builds an ext4 root filesystem from an Arch Linux ARM base.
- Collects kernel and initial RAM filesystem artifacts.
- Compresses the root image and generates SHA-256 checksums.
- Build scripts and image artifacts were present; no VM was launched in the inventory.
