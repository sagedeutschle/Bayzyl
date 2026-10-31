<!-- Project wording; the content below is intentionally separate from private inventory evidence. -->

## title
Audio Transcription Pipeline

## subtitle
Batch processing with visible progress and completeness checks.

## status
Experimental pipeline

## year
2026

## role
Batch orchestration, progress reporting and output-validation tooling.

## summary
A GPU-backed transcription experiment records progress for individual audio items and checks output completeness before treating a batch as finished. The recorded batch stopped when a timestamp validation failed. Human review and complete delivery remain outstanding.

## facts
- Phase: Experimental pipeline
- Stack: Python · GPU processing · JSON status tracking
- Review: Source inspected · 31 October 2026

## highlights
- Tracks completion, failures and pending work for individual items.
- Records machine-readable pipeline status.
- An automated timestamp check stopped the recorded batch.
- Transcripts had not received human verification.
