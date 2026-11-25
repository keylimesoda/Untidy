# Library batch concurrency

The v2 liked-track lookup now runs at most three 20-ID batches concurrently, retaining pagination inside each batch and the original batch order. Three watch trials returned all 264 tracks and 46 playlists and reduced the API completion marker median from 7.3 s to 5.0 s (worst 8.6 to 6.1 s). Conditions differed slightly after the reinstall, and this is not a UI-frame or battery measurement. See EXP-018 for the full conditions and remaining validation.
