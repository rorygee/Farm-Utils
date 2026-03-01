package com.farmutils.overlay.footprint;

import com.farmutils.model.PatchId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.runelite.api.coords.WorldPoint;

/**
 * Runtime-only registry mapping patch ids to explicit highlight footprints.
 */
public final class PatchFootprintRegistry
{
	private static final Map<PatchId, Footprint> FOOTPRINTS = new EnumMap<>(PatchId.class);

	static
	{
		// Farming Guild greenhouse (initial bootstrap)
		// Herb patch object: plane=0, x=1238, y=3726, dim_x=2, dim_y=2
		FOOTPRINTS.put(
			PatchId.HERB_FARMING_GUILD,
			Footprints.worldArea(new WorldPoint(1238, 3726, 0), new WorldPoint(1239, 3727, 0))
		);

		// Farming Guild greenhouse allotments (non-rect L-shapes)
		// Captured via RuneLite Tile Markers exports. Represented as unions of two rectangles.
		// NOTE: If plot numbering is reversed in your UI, swap Plot 1 and Plot 2 assignments below.
		final Set<WorldPoint> allotmentPlotA = new HashSet<>(18);
		addRect(allotmentPlotA, 0, 1267, 3732, 2, 5);
		addRect(allotmentPlotA, 0, 1267, 3732, 6, 2);
		FOOTPRINTS.put(PatchId.ALLOTMENT_FARMING_GUILD_PLOT_1, Footprints.tiles(allotmentPlotA));

		final Set<WorldPoint> allotmentPlotB = new HashSet<>(18);
		addRect(allotmentPlotB, 0, 1267, 3726, 6, 2);
		addRect(allotmentPlotB, 0, 1267, 3723, 2, 5);
		FOOTPRINTS.put(PatchId.ALLOTMENT_FARMING_GUILD_PLOT_2, Footprints.tiles(allotmentPlotB));

		// Flower patch (2x2)
		// Captured via RuneLite Tile Markers export in region 4922:
		// rects_region: sw=(44,13), w=2, h=2 -> world sw=(1260,3725), ne=(1261,3726)
		FOOTPRINTS.put(
			PatchId.FLOWER_FARMING_GUILD,
			Footprints.worldArea(new WorldPoint(1260, 3725, 0), new WorldPoint(1261, 3726, 0))
		);

		// Additional Farming Guild patches (rectangular footprints captured via Tile Markers)
		// Region 4922 base: (1216, 3712)
		FOOTPRINTS.put(
			PatchId.BUSH_FARMING_GUILD,
			Footprints.worldArea(new WorldPoint(1260, 3733, 0), new WorldPoint(1261, 3734, 0))
		);
		FOOTPRINTS.put(
			PatchId.CACTUS_FARMING_GUILD,
			Footprints.worldArea(new WorldPoint(1264, 3747, 0), new WorldPoint(1265, 3748, 0))
		);
		FOOTPRINTS.put(
			PatchId.TREE_FARMING_GUILD,
			Footprints.worldArea(new WorldPoint(1231, 3735, 0), new WorldPoint(1233, 3737, 0))
		);
		FOOTPRINTS.put(
			PatchId.SPECIAL_ANIMA,
			Footprints.worldArea(new WorldPoint(1231, 3722, 0), new WorldPoint(1233, 3724, 0))
		);
		FOOTPRINTS.put(
			PatchId.SPIRIT_TREE_FARMING_GUILD,
			Footprints.worldArea(new WorldPoint(1252, 3749, 0), new WorldPoint(1254, 3751, 0))
		);
		FOOTPRINTS.put(
			PatchId.SPECIAL_TREE_CELASTRUS_FARMING_GUILD,
			Footprints.worldArea(new WorldPoint(1243, 3749, 0), new WorldPoint(1245, 3751, 0))
		);
		FOOTPRINTS.put(
			PatchId.FRUIT_TREE_FARMING_GUILD,
			Footprints.worldArea(new WorldPoint(1242, 3758, 0), new WorldPoint(1243, 3759, 0))
		);
		FOOTPRINTS.put(
			PatchId.SPECIAL_TREE_REDWOOD_FARMING_GUILD,
			Footprints.worldArea(new WorldPoint(1225, 3751, 0), new WorldPoint(1232, 3758, 0))
		);

		// Hespori (instanced area) - region 5021 rects_region sw=(30,38), w=3, h=3
		// Converted using the same regionId -> base conversion as RuneLite tile markers.
		FOOTPRINTS.put(
			PatchId.SPECIAL_HESPORI,
			Footprints.worldArea(new WorldPoint(1246, 10086, 0), new WorldPoint(1248, 10088, 0))
		);

		// --- v1 patch highlights drop 2 ---
		// Note: For the various "Plot 1" vs "Plot 2" allotments, we follow the same convention
		// used for Farming Guild: Plot 1 is the more northern plot where applicable.

		// Falador (region 12083)
		putRectRegion(PatchId.HERB_FALADOR, 12083, 0, 50, 47, 2, 2);
		putRectRegion(PatchId.FLOWER_FALADOR, 12083, 0, 46, 43, 2, 2);
		putUnionRegion(PatchId.ALLOTMENT_FALADOR_PLOT_1, 12083, 0, 18,
			new int[][] { {42, 43, 2, 6}, {44, 47, 3, 2} }
		);
		putUnionRegion(PatchId.ALLOTMENT_FALADOR_PLOT_2, 12083, 0, 18,
			new int[][] { {50, 39, 2, 6}, {47, 39, 3, 2} }
		);

		// Hosidius (region 6967)
		putRectRegion(PatchId.HERB_HOSIDIUS, 6967, 0, 10, 30, 2, 2);
		putRectRegion(PatchId.FLOWER_HOSIDIUS, 6967, 0, 6, 34, 2, 2);
		putUnionRegion(PatchId.ALLOTMENT_HOSIDIUS_PLOT_1, 6967, 0, 22,
			new int[][] { {5, 38, 7, 2}, {10, 34, 2, 6} }
		);
		putUnionRegion(PatchId.ALLOTMENT_HOSIDIUS_PLOT_2, 6967, 0, 20,
			new int[][] { {4, 30, 4, 2}, {2, 30, 2, 6} }
		);

		// Civitas illa Fortis (region 6192)
		putRectRegion(PatchId.HERB_CIVITAS_ILLA_FORTIS, 6192, 0, 45, 22, 2, 2);
		putRectRegion(PatchId.FLOWER_CIVITAS_ILLA_FORTIS, 6192, 0, 49, 26, 2, 2);
		putUnionRegion(PatchId.ALLOTMENT_CIVITAS_ILLA_FORTIS_PLOT_1, 6192, 0, 18,
			new int[][] { {45, 26, 2, 6}, {47, 30, 3, 2} }
		);
		putUnionRegion(PatchId.ALLOTMENT_CIVITAS_ILLA_FORTIS_PLOT_2, 6192, 0, 18,
			new int[][] { {49, 22, 6, 2}, {53, 24, 2, 3} }
		);

		// Ardougne (region 10548)
		putRectRegion(PatchId.HERB_ARDOUGNE, 10548, 0, 46, 46, 2, 2);
		putRectRegion(PatchId.FLOWER_ARDOUGNE, 10548, 0, 42, 46, 2, 2);
		putUnionRegion(PatchId.ALLOTMENT_ARDOUGNE_PLOT_1, 10548, 0, 22,
			new int[][] { {38, 49, 2, 3}, {40, 50, 8, 2} }
		);
		putUnionRegion(PatchId.ALLOTMENT_ARDOUGNE_PLOT_2, 10548, 0, 22,
			new int[][] { {38, 42, 2, 3}, {40, 42, 8, 2} }
		);

		// Catherby (region 11062)
		putRectRegion(PatchId.HERB_CATHERBY, 11062, 0, 61, 7, 2, 2);
		putRectRegion(PatchId.FLOWER_CATHERBY, 11062, 0, 57, 7, 2, 2);
		putUnionRegion(PatchId.ALLOTMENT_CATHERBY_PLOT_1, 11062, 0, 22,
			new int[][] { {53, 10, 2, 3}, {55, 11, 8, 2} }
		);
		putUnionRegion(PatchId.ALLOTMENT_CATHERBY_PLOT_2, 11062, 0, 22,
			new int[][] { {53, 3, 2, 3}, {55, 3, 8, 2} }
		);

		// Port Phasmatys (region 14391)
		putRectRegion(PatchId.HERB_PORT_PHASMATYS, 14391, 0, 21, 9, 2, 2);
		putRectRegion(PatchId.FLOWER_PORT_PHASMATYS, 14391, 0, 17, 5, 2, 2);
		putUnionRegion(PatchId.ALLOTMENT_PORT_PHASMATYS_PLOT_1, 14391, 0, 18,
			new int[][] { {13, 9, 5, 2}, {13, 5, 2, 4} }
		);
		putUnionRegion(PatchId.ALLOTMENT_PORT_PHASMATYS_PLOT_2, 14391, 0, 18,
			new int[][] { {21, 1, 2, 6}, {18, 1, 3, 2} }
		);

		// Harmony Island (region 15148)
		putRectRegion(PatchId.HERB_HARMONY_ISLAND, 15148, 0, 13, 21, 2, 2);
		putRectRegion(PatchId.ALLOTMENT_HARMONY_ISLAND, 15148, 0, 18, 17, 1, 6);

		// Hosidius Spirit Tree (region 6711)
		putRectRegion(PatchId.SPIRIT_TREE_HOSIDIUS, 6711, 0, 28, 21, 3, 3);

		// Hosidius Vinery grapes (region 7223) - each tile is its own patch.
		// The mapping of tiles -> plot number is derived deterministically from the captured points
		// (sorted by y desc, then x asc) so it is stable and easy to adjust later if needed.
		putGrapeTilesRegion7223();

		// Bush patches
		putRectRegion(PatchId.BUSH_ARDOUGNE, 10290, 0, 57, 25, 2, 2);
		putRectRegion(PatchId.BUSH_RIMMINGTON, 11570, 0, 60, 21, 2, 2);

		// Remote herb patches
		putRectRegion(PatchId.HERB_WEISS, 11325, 0, 32, 30, 2, 2);
		putRectRegion(PatchId.HERB_TROLL_STRONGHOLD, 11321, 0, 10, 46, 2, 2);

		// --- v1 patch highlights drop 3 ---

		// Spirit trees
		putRectRegion(PatchId.SPIRIT_TREE_PORT_SARIM, 12082, 0, 51, 57, 3, 3);
		putRectRegion(PatchId.SPIRIT_TREE_BRIMHAVEN, 11058, 0, 49, 2, 3, 3);
		putRectRegion(PatchId.SPIRIT_TREE_ETCETERIA, 10300, 0, 52, 17, 3, 3);

		// Belladonna
		putRectRegion(PatchId.SPECIAL_BELLADONNA_DRAYNOR, 12340, 0, 14, 26, 2, 2);
		putRectRegion(PatchId.SPECIAL_BELLADONNA_AUBURNVALE, 5684, 0, 42, 26, 1, 1);

		// Fruit trees
		putRectRegion(PatchId.FRUIT_TREE_BRIMHAVEN, 11058, 0, 12, 12, 2, 2);
		putRectRegion(PatchId.FRUIT_TREE_GNOME_STRONGHOLD, 9781, 0, 43, 53, 2, 2);
		putRectRegion(PatchId.FRUIT_TREE_GNOME_VILLAGE, 9777, 0, 57, 43, 2, 2);
		putRectRegion(PatchId.FRUIT_TREE_KASTORI, 5423, 0, 5, 48, 2, 2);
		putRectRegion(PatchId.FRUIT_TREE_LLETYA, 9265, 0, 42, 25, 2, 2);
		putRectRegion(PatchId.FRUIT_TREE_CATHERBY, 11317, 0, 44, 41, 2, 2);

		// Calquat
		putRectRegion(PatchId.SPECIAL_TREE_CALQUAT_TAI_BWO_WANNAI, 11056, 0, 43, 28, 3, 3);
		putRectRegion(PatchId.SPECIAL_TREE_CALQUAT_SUMMER_SHORE, 12325, 0, 55, 36, 3, 3);
		putRectRegion(PatchId.SPECIAL_TREE_CALQUAT_KASTORI, 5423, 0, 22, 23, 3, 3);

		// Miscellania activity patches
		putRectRegion(PatchId.SPECIAL_MISCELLANIA_FLAX, 10044, 0, 28, 12, 4, 3);
		putRectRegion(PatchId.SPECIAL_MISCELLANIA_HERB, 10044, 0, 30, 8, 2, 2);

		// Bush patches
		putRectRegion(PatchId.BUSH_ETCETERIA, 10300, 0, 31, 23, 2, 2);
		putRectRegion(PatchId.BUSH_CHAMPIONS_GUILD, 12596, 0, 45, 29, 2, 2);

		// Mushrooms
		putRectRegion(PatchId.SPECIAL_MUSHROOM, 13622, 0, 59, 16, 2, 2);

		// Trees
		putRectRegion(PatchId.TREE_LUMBRIDGE, 12594, 0, 56, 30, 3, 3);
		putRectRegion(PatchId.TREE_GNOME_STRONGHOLD, 9781, 0, 3, 22, 3, 3);
		putRectRegion(PatchId.TREE_FALADOR, 11828, 0, 59, 44, 3, 3);
		putRectRegion(PatchId.TREE_TAVERLEY, 11573, 0, 55, 45, 3, 3);
		putRectRegion(PatchId.TREE_VARROCK, 12854, 0, 28, 2, 3, 3);
		putRectRegion(PatchId.TREE_NEMUS_RETREAT, 5427, 0, 21, 56, 3, 3);

		// Cactus
		putRectRegion(PatchId.CACTUS_AL_KHARID, 13106, 0, 51, 2, 2, 2);

		// Quest / special patches
		putRectRegion(PatchId.QUEST_ENRICHED_SNAPDRAGON, 11828, 3, 18, 10, 2, 2);
		putRectRegion(PatchId.QUEST_MAGIC_BEANS, 11573, 0, 41, 32, 3, 3);
		putRectRegion(PatchId.QUEST_UNFERTHS_PATCH, 11575, 0, 37, 42, 3, 4);
		putRectRegion(PatchId.QUEST_ELDER_CADANTINE, 9265, 0, 17, 15, 2, 2);

		// Additional non-allotment flower patch
		putRectRegion(PatchId.FLOWER_KASTORI, 5423, 0, 8, 14, 2, 2);

		// Special trees / hardwood
		// Convention: Plot 1 = west, Plot 2 = south, Plot 3 = east.
		putRectRegion(PatchId.SPECIAL_TREE_HARDWOOD_FOSSIL_ISLAND_PLOT_1, 14651, 0, 53, 60, 3, 3);
		putRectRegion(PatchId.SPECIAL_TREE_HARDWOOD_FOSSIL_ISLAND_PLOT_2, 14651, 0, 59, 56, 3, 3);
		putRectRegion(PatchId.SPECIAL_TREE_HARDWOOD_FOSSIL_ISLAND_PLOT_3, 14907, 0, 2, 58, 3, 3);
		putRectRegion(PatchId.SPECIAL_TREE_HARDWOOD_ANGLERS_RETREAT, 9770, 0, 38, 14, 3, 3);
		putRectRegion(PatchId.SPECIAL_TREE_HARDWOOD_LOCUS_OASIS, 6702, 0, 22, 27, 3, 3);

		// Underwater (seaweed)
		// Convention: Plot 1 = north, Plot 2 = south.
		putRectRegion(PatchId.SPECIAL_SEAWEED_PLOT_1, 15008, 1, 21, 33, 2, 2);
		putRectRegion(PatchId.SPECIAL_SEAWEED_PLOT_2, 15008, 1, 21, 27, 2, 2);

		// Coral nurseries
		// Convention: Plot 1 = west, Plot 2 = east.
		putRectRegion(PatchId.SPECIAL_CORAL_NURSERY_PLOT_1, 13194, 1, 30, 28, 2, 2);
		putRectRegion(PatchId.SPECIAL_CORAL_NURSERY_PLOT_2, 13194, 1, 35, 28, 2, 2);

		// --- v1 patch highlights: hops ---
		// Important: these footprints are explicit tiles/rects and are intentionally NOT derived
		// from scene scanning or object-name matching.

		// Lumbridge hops
		// Capture note: user export included an additional 3x3 block in region 12594 which exactly matches
		// the Lumbridge tree patch (TREE_LUMBRIDGE). To avoid false positives, we only register the 5x5
		// block in region 12851 here.
		putRectRegion(PatchId.HOPS_LUMBRIDGE, 12851, 0, 27, 49, 5, 5);

		// Yanille hops (4x4)
		putRectRegion(PatchId.HOPS_YANILLE, 10288, 0, 14, 31, 4, 4);

		// McGrubor's Wood hops (6x6)
		putRectRegion(PatchId.HOPS_MCGUBORS_WOOD, 10551, 0, 40, 3, 6, 6);

		// Aldarin hops (4x4)
		putRectRegion(PatchId.HOPS_ALDARIN, 5421, 0, 19, 57, 4, 4);

		// Entrana hops (4x4)
		putRectRegion(PatchId.HOPS_ENTRANA, 11060, 0, 57, 7, 4, 4);

		// Kelda hops (quest patch)
		putRectRegion(PatchId.QUEST_KELDA_HOPS, 11423, 0, 37, 27, 4, 4);

		// --- Prifddinas ---
		// Crystal tree (2x2)
		putRectRegion(PatchId.SPECIAL_TREE_CRYSTAL_PRIFDDINAS, 13151, 0, 27, 38, 2, 2);

		// Allotments (non-rect unions)
		putUnionRegion(PatchId.ALLOTMENT_PRIFDDINAS_PLOT_1, 13151, 0, 16,
			new int[][] { {26, 17, 6, 2}, {26, 19, 2, 2} }
		);
		putUnionRegion(PatchId.ALLOTMENT_PRIFDDINAS_PLOT_2, 13151, 0, 16,
			new int[][] { {24, 23, 6, 2}, {24, 21, 2, 2} }
		);
	}

	private static void addRect(final Set<WorldPoint> out, final int plane,
		final int southWestX, final int southWestY, final int width, final int height)
	{
		for (int x = southWestX; x < southWestX + width; x++)
		{
			for (int y = southWestY; y < southWestY + height; y++)
			{
				out.add(new WorldPoint(x, y, plane));
			}
		}
	}

	private static void addRectRegion(final Set<WorldPoint> out, final int regionId, final int plane,
		final int regionSouthWestX, final int regionSouthWestY, final int width, final int height)
	{
		final int baseX = regionBaseX(regionId);
		final int baseY = regionBaseY(regionId);
		addRect(out, plane, baseX + regionSouthWestX, baseY + regionSouthWestY, width, height);
	}

	private static void putRectRegion(final PatchId patchId, final int regionId, final int plane,
		final int regionSouthWestX, final int regionSouthWestY, final int width, final int height)
	{
		final int baseX = regionBaseX(regionId);
		final int baseY = regionBaseY(regionId);
		final int worldSwX = baseX + regionSouthWestX;
		final int worldSwY = baseY + regionSouthWestY;
		final int worldNeX = worldSwX + width - 1;
		final int worldNeY = worldSwY + height - 1;
		FOOTPRINTS.put(
			patchId,
			Footprints.worldArea(new WorldPoint(worldSwX, worldSwY, plane), new WorldPoint(worldNeX, worldNeY, plane))
		);
	}

	private static void putUnionRegion(final PatchId patchId, final int regionId, final int plane,
		final int expectedTileCount, final int[][] rects)
	{
		final Set<WorldPoint> tiles = new HashSet<>(expectedTileCount);
		for (int i = 0; i < rects.length; i++)
		{
			final int[] r = rects[i];
			addRectRegion(tiles, regionId, plane, r[0], r[1], r[2], r[3]);
		}
		FOOTPRINTS.put(patchId, Footprints.tiles(tiles));
	}

	private static int regionBaseX(final int regionId)
	{
		return (regionId >> 8) * 64;
	}

	private static int regionBaseY(final int regionId)
	{
		return (regionId & 0xFF) * 64;
	}

	private static void putGrapeTilesRegion7223()
	{
		final int regionId = 7223;
		final int plane = 0;
		final int baseX = regionBaseX(regionId);
		final int baseY = regionBaseY(regionId);

		final List<WorldPoint> points = new ArrayList<>(12);
		// points_region provided by user (region 7223)
		points.add(new WorldPoint(baseX + 15, baseY + 42, plane));
		points.add(new WorldPoint(baseX + 16, baseY + 42, plane));
		points.add(new WorldPoint(baseX + 15, baseY + 40, plane));
		points.add(new WorldPoint(baseX + 16, baseY + 40, plane));
		points.add(new WorldPoint(baseX + 15, baseY + 38, plane));
		points.add(new WorldPoint(baseX + 16, baseY + 38, plane));
		points.add(new WorldPoint(baseX + 15, baseY + 33, plane));
		points.add(new WorldPoint(baseX + 16, baseY + 33, plane));
		points.add(new WorldPoint(baseX + 15, baseY + 31, plane));
		points.add(new WorldPoint(baseX + 16, baseY + 31, plane));
		points.add(new WorldPoint(baseX + 15, baseY + 29, plane));
		points.add(new WorldPoint(baseX + 16, baseY + 29, plane));

		points.sort((a, b) ->
		{
			if (a.getY() != b.getY())
			{
				return Integer.compare(b.getY(), a.getY());
			}
			return Integer.compare(a.getX(), b.getX());
		});

		final PatchId[] grapeIds = new PatchId[] {
			PatchId.SPECIAL_GRAPES_PLOT_1,
			PatchId.SPECIAL_GRAPES_PLOT_2,
			PatchId.SPECIAL_GRAPES_PLOT_3,
			PatchId.SPECIAL_GRAPES_PLOT_4,
			PatchId.SPECIAL_GRAPES_PLOT_5,
			PatchId.SPECIAL_GRAPES_PLOT_6,
			PatchId.SPECIAL_GRAPES_PLOT_7,
			PatchId.SPECIAL_GRAPES_PLOT_8,
			PatchId.SPECIAL_GRAPES_PLOT_9,
			PatchId.SPECIAL_GRAPES_PLOT_10,
			PatchId.SPECIAL_GRAPES_PLOT_11,
			PatchId.SPECIAL_GRAPES_PLOT_12
		};

		for (int i = 0; i < grapeIds.length && i < points.size(); i++)
		{
			final Set<WorldPoint> tile = new HashSet<>(1);
			tile.add(points.get(i));
			FOOTPRINTS.put(grapeIds[i], Footprints.tiles(tile));
		}
	}

	private PatchFootprintRegistry()
	{
	}

	@Nullable
	public static Footprint get(final PatchId patchId)
	{
		return FOOTPRINTS.get(patchId);
	}
}
