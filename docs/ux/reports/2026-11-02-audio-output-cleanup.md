# Audio output and actions sheet

The former app-owned output picker exposed connected endpoints rather than the system's full selection flow. The Audio output row now opens the system picker, shows the reported media route, and clears a previously selected app-specific output when the sheet opens. The user confirmed that AirPods Max playback worked after this change. This observation does not establish the precise cause of the earlier silence. Device-label refresh and cancellation remain to be exercised.

The actions sheet's handle is replaced by a 48dp close control with an accessibility label (UX-028). Debug assembly and app unit tests pass; a release build was installed on the watch. The shell cannot directly launch the non-exported player activity; interactive verification of dismissal remains pending. The obsolete app picker implementation was removed after the release install; this removal does not change the installed behavior.

Library latency (UX-029) remains open. The existing ~24-second observation is a single sample, not a performance distribution. The fallback currently loads the full liked-tracks listing before albums, artists, and playlists; any progressive-loading change needs to preserve full pagination, fallback and cancellation semantics, then be compared in at least three controlled watch trials per side.
