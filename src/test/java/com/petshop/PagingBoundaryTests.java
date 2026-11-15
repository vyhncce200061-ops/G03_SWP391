package com.petshop;

import com.petshop.util.PageRequests;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class PagingBoundaryTests {
    @ParameterizedTest @ValueSource(ints = {-1, 0, 1, 10, 100, Integer.MAX_VALUE})
    void offsetAndDisplayedPageStayInRangeForUntrustedPageSizes(int size) {
        var page=PageRequests.bounded(Integer.MAX_VALUE,size);
        assertThat(page.getPageSize()).isBetween(1,100);
        assertThat(page.getOffset()).isBetween(0L,(long)Integer.MAX_VALUE);
        assertThat(page.getPageNumber()+1).isPositive();
        assertThat(PageRequests.bounded(-1,size).getPageNumber()).isZero();
    }
}
