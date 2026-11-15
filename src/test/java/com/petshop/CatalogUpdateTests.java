package com.petshop;

import com.petshop.dto.catalog.ProductSummaryDto;
import com.petshop.entity.*;
import com.petshop.entity.enums.*;
import com.petshop.repository.*;
import com.petshop.service.EmailService;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.HtmlUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:catalogUpdate;DB_CLOSE_DELAY=-1;MODE=MSSQLServer;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.jpa.open-in-view=false","spring.jpa.show-sql=false","logging.level.org.hibernate.SQL=WARN"})
@AutoConfigureMockMvc @ActiveProfiles("test")
class CatalogUpdateTests {
    @MockBean EmailService mail;
    @Autowired MockMvc mvc;
    @Autowired RoleRepository roles;
    @Autowired UserRepository users;
    @Autowired CategoryRepository categories;
    @Autowired BrandRepository brands;
    @Autowired ProductRepository products;
    @Autowired ProductVariantRepository variants;
    String key,keyword;
    User author;
    Category category;
    Brand brand;
    Map<String,Product> records;

    @BeforeEach void fixture(){
        key=UUID.randomUUID().toString().replace("-","").substring(0,12);
        keyword="Catalog A&B "+key;
        var role=roles.findByCode("CUSTOMER").orElseGet(()->roles.save(Role.builder().code("CUSTOMER").name("Customer").build()));
        author=users.save(User.builder().role(role).fullName("Catalog customer").email(key+"@catalog.test").passwordHash("unused-hash").build());
        category=categories.save(Category.builder().code("CAT"+key).name("Category "+key).build());
        brand=brands.save(Brand.builder().name("Brand "+key).build());
        records=new LinkedHashMap<>();
        for(String amount:List.of("99999","100000","200000","300000","500000","500001")) records.put(amount,priced(amount,new BigDecimal(amount),10));
        var hidden=priced("hidden",new BigDecimal("50000"),10);hidden.setStatus(ProductStatus.HIDDEN);products.save(hidden);
        var inactive=product("inactive");variants.save(ProductVariant.builder().product(inactive).skuCode(key+"inactive").variantName("Hidden SKU")
                .price(BigDecimal.TEN).stockQuantity(10).status(ProductVariantStatus.INACTIVE).build());
    }
    Product product(String label){
        return products.save(Product.builder().name(keyword+" "+label).slug("catalog-"+key+"-"+label)
                .category(category).brand(brand).petType(PetType.DOG).createdBy(author).build());
    }
    Product priced(String label,BigDecimal price,int stock){
        var product=product(label);
        variants.save(ProductVariant.builder().product(product).skuCode(key+label).variantName("Standard").price(price).stockQuantity(stock).build());
        return product;
    }
    @SuppressWarnings("unchecked") Page<ProductSummaryDto> page(MvcResult result){
        return (Page<ProductSummaryDto>) result.getModelAndView().getModel().get("productPage");
    }
    Set<Long> ids(Page<ProductSummaryDto> page){
        return page.stream().map(ProductSummaryDto::getId).collect(java.util.stream.Collectors.toSet());
    }
    String attribute(String tag,String name){
        var match=Pattern.compile("\\b"+name+"=\"([^\"]*)\"").matcher(tag);
        return match.find()?HtmlUtils.htmlUnescape(match.group(1)):null;
    }
    Map<String,String> query(String link){
        var values=new LinkedHashMap<String,String>();String raw=URI.create(link).getRawQuery();
        for(String item:raw.split("&")){String[] part=item.split("=",2);values.put(URLDecoder.decode(part[0],StandardCharsets.UTF_8),part.length==1?"":URLDecoder.decode(part[1],StandardCharsets.UTF_8));}
        return values;
    }
    String next(String html){
        var tags=Pattern.compile("<a\\b[^>]*>").matcher(html);
        while(tags.find()){
            String css=attribute(tags.group(),"class"),link=attribute(tags.group(),"href");
            if(css!=null&&css.contains("page-link")&&link!=null&&"1".equals(query(link).get("page")))return link;
        }throw new AssertionError("Expected second-page link");
    }

    @Test void priceRangesKeepExistingBoundariesAndExcludeInactiveProducts() throws Exception {
        var ranges=Map.of("under100",List.of("99999"),"100to300",List.of("100000","200000","300000"),
                "300to500",List.of("300000","500000"),"over500",List.of("500001"));
        for(var entry:ranges.entrySet()){
            var result=mvc.perform(get("/products").param("keyword",keyword).param("priceRange",entry.getKey()))
                    .andExpect(status().isOk()).andExpect(model().attribute("selectedPriceRange",entry.getKey())).andReturn();
            Set<Long> expected=entry.getValue().stream().map(records::get).map(Product::getId).collect(java.util.stream.Collectors.toSet());
            assertEquals(expected,ids(page(result)),entry.getKey());
        }
    }

    @Test void dropdownAndPaginationKeepKeywordCategoryBrandPetTypeAndPriceRange() throws Exception {
        var first=mvc.perform(get("/products").param("keyword",keyword).param("categoryId",category.getId().toString())
                .param("brandId",brand.getId().toString()).param("petType"," dog ").param("sort","priceAsc")
                .param("priceRange","100to300").param("size","1"))
                .andExpect(status().isOk()).andExpect(model().attribute("selectedPetType","DOG")).andReturn();
        String html=first.getResponse().getContentAsString(),link=next(html);var q=query(link);
        assertEquals(keyword,q.get("keyword"));assertEquals(category.getId().toString(),q.get("categoryId"));
        assertEquals(brand.getId().toString(),q.get("brandId"));assertEquals("DOG",q.get("petType"));
        assertEquals("100to300",q.get("priceRange"));assertEquals("price_asc",q.get("sortBy"));assertEquals("1",q.get("size"));
        var inputs=Pattern.compile("<input\\b[^>]*>").matcher(html);String size=null;
        while(inputs.find())if("size".equals(attribute(inputs.group(),"name")))size=attribute(inputs.group(),"value");
        assertEquals("1",size);
        var second=mvc.perform(get(URI.create(link))).andExpect(status().isOk()).andExpect(model().attribute("selectedPriceRange","100to300")).andReturn();
        assertEquals(records.get("200000").getId(),page(second).getContent().get(0).getId());
        assertTrue(second.getResponse().getContentAsString().contains("200.000 đ"));
    }

    @Test void explicitLegacyPriceBoundsAndSortSurviveNextPage() throws Exception {
        var higher=priced("201000",new BigDecimal("201000"),5);
        var first=mvc.perform(get("/products").param("keyword",keyword).param("minPrice","190000").param("maxPrice","210000")
                .param("sort","priceDesc").param("size","1")).andExpect(status().isOk()).andReturn();
        assertEquals(higher.getId(),page(first).getContent().get(0).getId());
        String link=next(first.getResponse().getContentAsString());
        assertEquals("190000",query(link).get("minPrice"));assertEquals("210000",query(link).get("maxPrice"));
        var second=mvc.perform(get(URI.create(link))).andExpect(status().isOk()).andReturn();
        assertEquals(2,page(second).getTotalElements());assertEquals(records.get("200000").getId(),page(second).getContent().get(0).getId());
    }

    @Test void extremePaginationIsBoundedAndMalformedParametersAreClientErrors() throws Exception {
        var result=mvc.perform(get("/products").param("keyword",keyword).param("page",Integer.toString(Integer.MAX_VALUE))
                .param("size",Integer.toString(Integer.MAX_VALUE))).andExpect(status().isOk()).andReturn();
        assertEquals(100,page(result).getSize());assertTrue(page(result).getPageable().getOffset()<=Integer.MAX_VALUE);
        for(String parameter:List.of("page","size","categoryId","brandId","minPrice","maxPrice"))
            mvc.perform(get("/products").param(parameter,"invalid")).andExpect(status().is4xxClientError());
    }

    @Test void noActiveSkuShowsNoSellingPriceInsteadOfInventingZero() throws Exception {
        var result=mvc.perform(get("/products").param("keyword",keyword+" inactive")).andExpect(status().isOk()).andReturn();
        assertEquals(1,page(result).getTotalElements());
        assertNull(page(result).getContent().get(0).getMinPrice());assertNull(page(result).getContent().get(0).getMaxPrice());
        assertTrue(result.getResponse().getContentAsString().contains("Chưa có giá bán"));
        assertFalse(result.getResponse().getContentAsString().contains(">0 đ<"));
    }

    @Test void existingZeroAndOutOfStockPricesArePreserved() throws Exception {
        priced("zero",BigDecimal.ZERO,0);
        var free=mvc.perform(get("/products").param("keyword",keyword+" zero")).andExpect(status().isOk()).andReturn();
        assertEquals(0,BigDecimal.ZERO.compareTo(page(free).getContent().get(0).getMinPrice()));
        var existing=variants.findByProductId(records.get("200000").getId()).get(0);existing.setStockQuantity(0);variants.save(existing);
        var out=mvc.perform(get("/products").param("keyword",keyword+" 200000")).andExpect(status().isOk()).andReturn();
        assertEquals(0,new BigDecimal("200000").compareTo(page(out).getContent().get(0).getMinPrice()));
    }
}
