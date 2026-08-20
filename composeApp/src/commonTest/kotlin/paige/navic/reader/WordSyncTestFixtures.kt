package paige.navic.reader

import paige.navic.domain.repositories.BinderyWhispersyncIdentity

internal object WordSyncTestFixtures {
	fun identity(artifactId: Long = 17): BinderyWhispersyncIdentity = BinderyWhispersyncIdentity(
		bookId = 7,
		ebookBookFileId = 11,
		audiobookBookFileId = 13,
		artifactId = artifactId
	)

	fun indexJson(
		artifactId: Long = 17,
		generatedAt: String = "2026-08-03T00:00:00Z"
	): String =
		"""
		{
		  "schema": "bindery.whispersync.wordsync.index.v1",
		  "version": 1,
		  "bookId": 7,
		  "ebookBookFileId": 11,
		  "audiobookBookFileId": 13,
		  "artifactId": $artifactId,
		  "generatedAt": "$generatedAt",
		  "timeScale": 1000,
		  "coordinateBasis": {
		    "extractor": "bindery-epub-text",
		    "extractorVersion": "1",
		    "normalization": "raw-extracted-text-offsets",
		    "ebookTextHash": "sha256:${"a".repeat(64)}"
		  },
		  "statusEnum": {
		    "0": "unmatched-audio",
		    "1": "exact",
		    "2": "normalized",
		    "3": "fuzzy",
		    "4": "semantic-number",
		    "5": "review"
		  },
		  "methodEnum": {
		    "0": "asr-word-timestamp",
		    "1": "forced-align-cue-window",
		    "2": "cue-interpolated-review"
		  },
		  "chapters": [
		    {
		      "chapterKey": "spine-002-chapter",
		      "spineIndex": 2,
		      "ebookHref": "Text/chapter.xhtml",
		      "path": "spine-002-chapter.wsyncw",
		      "href": "/api/v1/sync/artifacts/$artifactId/wordsync/spine-002-chapter",
		      "opdsHref": "/opds/books/7/sync/$artifactId/wordsync/spine-002-chapter",
		      "ebookStart": 100,
		      "ebookEnd": 109,
		      "audioRanges": [
		        {
		          "audioResourceId": "audio-a",
		          "audioTrackIndex": 0,
		          "audioHref": "Audio/a.mp3",
		          "startMs": 1000,
		          "endMs": 1520
		        },
		        {
		          "audioResourceId": "audio-b",
		          "audioTrackIndex": 1,
		          "audioHref": "Audio/b.mp3",
		          "startMs": 2000,
		          "endMs": 2300
		        }
		      ],
		      "audioWordCount": 3,
		      "matchedAudioWordCount": 2,
		      "reviewAudioWordCount": 1,
		      "unmatchedAudioWordCount": 0,
		      "unmatchedEbookWordCount": 0,
		      "minConfidence": 95,
		      "meanConfidence": 97
		    }
		  ]
		}
		""".trimIndent()

	fun chapterJson(artifactId: Long = 17): String =
		"""
		{
		  "schema": "bindery.whispersync.wordsync.chapter.v1",
		  "version": 1,
		  "bookId": 7,
		  "ebookBookFileId": 11,
		  "audiobookBookFileId": 13,
		  "artifactId": $artifactId,
		  "chapterKey": "spine-002-chapter",
		  "ebookHref": "Text/chapter.xhtml",
		  "spineIndex": 2,
		  "ebookStart": 100,
		  "ebookEnd": 109,
		  "timeScale": 1000,
		  "tracks": [
		    {
		      "audioResourceId": "audio-a",
		      "audioTrackIndex": 0,
		      "audioHref": "Audio/a.mp3",
		      "baseStartMs": 1000,
		      "audioStartDeltaMs": [0, 300],
		      "audioDurMs": [200, 220],
		      "ebookStartDelta": [0, 3],
		      "ebookLen": [2, 1],
		      "cueId": [1, 1],
		      "status": [1, 3],
		      "confidence": [98, 97],
		      "method": [0, 0],
		      "flags": [0, 0]
		    },
		    {
		      "audioResourceId": "audio-b",
		      "audioTrackIndex": 1,
		      "audioHref": "Audio/b.mp3",
		      "baseStartMs": 2000,
		      "audioStartDeltaMs": [0],
		      "audioDurMs": [300],
		      "ebookStartDelta": [5],
		      "ebookLen": [4],
		      "cueId": [2],
		      "status": [5],
		      "confidence": [95],
		      "method": [1],
		      "flags": [0]
		    }
		  ],
		  "ebookLookup": {
		    "ebookStart": [100, 103, 105],
		    "trackIndex": [0, 0, 1],
		    "wordIndex": [0, 1, 0]
		  },
		  "unmatchedEbook": []
		}
		""".trimIndent()
}
