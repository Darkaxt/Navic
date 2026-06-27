import unittest

from artist_credit_resolution_poc import (
    ArtistCreditCache,
    CompositeArtistIndex,
    CreditContext,
    InMemoryArtistIndex,
    ResolutionStatus,
    decode_storage_value,
    immediate_render,
    resolve_credit,
    split_artist_credit,
)


class ArtistCreditResolutionPocTest(unittest.TestCase):
    def test_structured_artists_override_dirty_album_artist_credit(self):
        index = InMemoryArtistIndex(artists={"Eric Buchholz", "Braxton Burks"})
        context = CreditContext(
            original_credit=(
                "Eric Buchholz & Braxton Burks, "
                "Eric Buchholz • Eric Buchholz & Braxton Burks"
            ),
            album_title="Pokemon Reorchestrated: Double Team!",
            structured_artists=("Eric Buchholz", "Braxton Burks"),
        )

        resolution = resolve_credit(context, index)

        self.assertEqual(resolution.status, ResolutionStatus.RESOLVED)
        self.assertEqual(resolution.display_names, ("Eric Buchholz", "Braxton Burks"))
        self.assertEqual(resolution.reason, "structured-artists")

    def test_single_structured_artist_same_as_original_does_not_block_split(self):
        index = InMemoryArtistIndex(artists={"Anyma", "LISA"})
        context = CreditContext(
            original_credit="Anyma & LISA",
            structured_artists=("Anyma & LISA",),
        )

        resolution = resolve_credit(context, index)

        self.assertEqual(resolution.status, ResolutionStatus.RESOLVED)
        self.assertEqual(resolution.display_names, ("Anyma", "LISA"))
        self.assertEqual(resolution.reason, "validated-split")

    def test_known_group_is_not_split_even_when_name_contains_delimiters(self):
        index = InMemoryArtistIndex(
            artists={"Earth, Wind & Fire", "Earth", "Wind", "Fire"}
        )
        context = CreditContext(original_credit="Earth, Wind & Fire")

        resolution = resolve_credit(context, index)

        self.assertEqual(resolution.status, ResolutionStatus.RESOLVED)
        self.assertEqual(resolution.display_names, ("Earth, Wind & Fire",))
        self.assertEqual(resolution.reason, "exact-full-credit")

    def test_validated_delimiter_split_resolves_composite_artist_credit(self):
        index = InMemoryArtistIndex(artists={"Anyma", "LISA"})
        context = CreditContext(original_credit="Anyma & LISA", track_title="Bad Angel")

        resolution = resolve_credit(context, index)

        self.assertEqual(resolution.status, ResolutionStatus.RESOLVED)
        self.assertEqual(resolution.display_names, ("Anyma", "LISA"))
        self.assertEqual(resolution.reason, "validated-split")

    def test_unsafe_split_is_rejected_when_any_candidate_is_unknown(self):
        index = InMemoryArtistIndex(artists={"Chase"})
        context = CreditContext(original_credit="Chase & Status")

        resolution = resolve_credit(context, index)

        self.assertEqual(resolution.status, ResolutionStatus.UNRESOLVED)
        self.assertEqual(resolution.display_names, ("Chase & Status",))
        self.assertEqual(resolution.reason, "candidate-not-found")

    def test_album_context_can_confirm_split_when_direct_candidate_is_ambiguous(self):
        index = InMemoryArtistIndex(
            artists={"Afrojack", "Sia", "David Guetta"},
            albums={
                "Titanium Single": ("Afrojack", "Sia", "David Guetta"),
            },
        )
        context = CreditContext(
            original_credit="Afrojack, Sia & David Guetta",
            album_title="Titanium Single",
        )

        resolution = resolve_credit(context, index)

        self.assertEqual(resolution.status, ResolutionStatus.RESOLVED)
        self.assertEqual(
            resolution.display_names,
            ("Afrojack", "Sia", "David Guetta"),
        )
        self.assertEqual(resolution.reason, "album-context")

    def test_pending_render_uses_raw_credit_until_cache_entry_exists(self):
        cache = ArtistCreditCache()
        context = CreditContext(original_credit="Anyma & LISA", track_title="Bad Angel")

        self.assertEqual(immediate_render(context, cache), ("Anyma & LISA",))

        cache.store(context, ("Anyma", "LISA"), "validated-split")

        self.assertEqual(immediate_render(context, cache), ("Anyma", "LISA"))

    def test_split_artist_credit_dedupes_repeated_segments(self):
        self.assertEqual(
            split_artist_credit(
                "Eric Buchholz & Braxton Burks, "
                "Eric Buchholz • Eric Buchholz & Braxton Burks"
            ),
            ("Eric Buchholz", "Braxton Burks"),
        )

    def test_composite_artist_index_uses_secondary_lookup(self):
        primary = InMemoryArtistIndex(artists={"Anyma"})
        secondary = InMemoryArtistIndex(artists={"LISA"})
        index = CompositeArtistIndex((primary, secondary))
        context = CreditContext(original_credit="Anyma & LISA")

        resolution = resolve_credit(context, index)

        self.assertEqual(resolution.status, ResolutionStatus.RESOLVED)
        self.assertEqual(resolution.display_names, ("Anyma", "LISA"))

    def test_decode_storage_value_handles_firefox_byte_rows(self):
        self.assertEqual(decode_storage_value(b"Darkaxt"), "Darkaxt")
        self.assertEqual(decode_storage_value('"Darkaxt"'), "Darkaxt")

    def test_decode_storage_value_skips_binary_rows(self):
        self.assertEqual(decode_storage_value(b"\xa6\x00"), "")


if __name__ == "__main__":
    unittest.main()
