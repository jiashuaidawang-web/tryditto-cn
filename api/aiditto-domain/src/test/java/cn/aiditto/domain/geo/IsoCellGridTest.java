package cn.aiditto.domain.geo;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IsoCellGridTest {

    @Test
    void haversineIsSane() {
        assertThat(IsoCellGrid.haversineKm(39.9929, 116.3310, 39.9929, 116.3310)).isEqualTo(0.0);
        // 北京两点约 1km 量级
        assertThat(IsoCellGrid.haversineKm(39.9929, 116.3310, 39.9929, 116.3430)).isBetween(0.9, 1.2);
    }

    @Test
    void radiusIsConservativeAndCapped() {
        assertThat(IsoCellGrid.radiusKm("walk", 30)).isLessThan(IsoCellGrid.radiusKm("metro", 30));
        assertThat(IsoCellGrid.radiusKm("drive", 9999)).isEqualTo(IsoCellGrid.MAX_RADIUS_KM);
        assertThat(IsoCellGrid.radiusKm(null, null)).isGreaterThan(0);
        assertThat(IsoCellGrid.radiusKm("walk", 0)).isGreaterThanOrEqualTo(1.0);
    }

    @Test
    void cellsAreDeterministicBoundedAndPiiFree() {
        List<String> a = IsoCellGrid.cells(39.9929, 116.3310, "transit", 30);
        List<String> b = IsoCellGrid.cells(39.9929, 116.3310, "transit", 30);
        assertThat(a).isEqualTo(b).isNotEmpty();
        assertThat(a.size()).isLessThanOrEqualTo(IsoCellGrid.MAX_CELLS);
        // 只输出格网编号，绝不含坐标/地址
        assertThat(a).allSatisfy(s -> assertThat(s).matches("c-?\\d+_-?\\d+"));
        assertThat(String.join(",", a)).doesNotContain("39.", "116.", "@", "路", "区");
    }

    @Test
    void nearbyUsersShareCellsFarOnesDoNot() {
        List<String> near = IsoCellGrid.cells(39.9929, 116.3310, "transit", 30);
        List<String> samePlace = IsoCellGrid.cells(39.9931, 116.3312, "transit", 30);
        List<String> far = IsoCellGrid.cells(31.2304, 121.4737, "transit", 30); // 上海
        assertThat(IsoCellGrid.overlaps(near, samePlace)).isTrue();
        assertThat(IsoCellGrid.overlaps(near, far)).isFalse();
    }

    @Test
    void largerCommuteRadiusExpandsReach() {
        List<String> small = IsoCellGrid.cells(39.9929, 116.3310, "walk", 10);
        List<String> large = IsoCellGrid.cells(39.9929, 116.3310, "metro", 60);
        assertThat(large.size()).isGreaterThan(small.size());
    }

    @Test
    void missingCoordinatesMeanNoGeoConstraint() {
        assertThat(IsoCellGrid.cells(null, null, "transit", 30)).isEmpty();
        assertThat(IsoCellGrid.cells(999.0, 116.0, "transit", 30)).isEmpty();
        // 空集 = 不施加约束，不误杀缺坐标用户
        assertThat(IsoCellGrid.overlaps(List.of(), List.of("c1_2"))).isTrue();
        assertThat(IsoCellGrid.overlaps(List.of("c1_2"), List.of())).isTrue();
    }

    @Test
    void cellIdIsStable() {
        assertThat(IsoCellGrid.cellId(39.9929, 116.3310))
                .isEqualTo(IsoCellGrid.cellId(39.9929, 116.3310));
        assertThat(IsoCellGrid.cellId(null, null)).isEmpty();
    }

    @Test
    void cellsNeverHitTheTruncationCapForCampusCommute() {
        // 0.02° 网格 + 8km 封顶：典型校园通勤不应触发方向性截断
        for (String mode : new String[]{"walk", "transit", "metro", "drive"}) {
            for (int minutes : new int[]{15, 30, 45, 60, 90}) {
                List<String> c = IsoCellGrid.cells(39.9929, 116.3310, mode, minutes);
                assertThat(c.size()).as("%s/%dmin", mode, minutes)
                        .isPositive().isLessThan(IsoCellGrid.MAX_CELLS);
            }
        }
    }

    @Test
    void wildcardIsSymmetricForWriteSideAndEmptyForSelfWithoutGeo() {
        assertThat(IsoCellGrid.cellsOrWildcard(null, null, "transit", 30))
                .containsExactly(IsoCellGrid.WILDCARD);
        assertThat(IsoCellGrid.queryCells(null, null, "transit", 30)).isEmpty();
        assertThat(IsoCellGrid.queryCells(39.9929, 116.3310, "transit", 30))
                .contains(IsoCellGrid.WILDCARD);
    }

    @Test
    void onlineMatchesIsDirectionalAndEquivalentToQdrantMatchAny() {
        List<String> query = IsoCellGrid.queryCells(39.9929, 116.3310, "transit", 30);
        List<String> nearby = IsoCellGrid.cellsOrWildcard(39.9931, 116.3312, "transit", 30);
        List<String> shanghai = IsoCellGrid.cellsOrWildcard(31.2304, 121.4737, "transit", 30);
        List<String> noGeo = List.of(IsoCellGrid.WILDCARD);

        assertThat(IsoCellGrid.matches(query, nearby)).isTrue();
        assertThat(IsoCellGrid.matches(query, shanghai)).isFalse();  // 跨城不得放行
        assertThat(IsoCellGrid.matches(query, noGeo)).isTrue();      // 无地理信息放行
        assertThat(IsoCellGrid.matches(List.of(), shanghai)).isTrue(); // 自己无约束则全部放行
        // 对称 overlaps 只用于离线对照：任一侧通配即放行（不用于在线匹配）
        assertThat(IsoCellGrid.overlaps(query, shanghai)).isTrue();
    }
}
