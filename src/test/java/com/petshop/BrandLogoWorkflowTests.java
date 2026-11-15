package com.petshop;

import com.petshop.dto.catalog.BrandFormDto;
import com.petshop.entity.Brand;
import com.petshop.entity.Category;
import com.petshop.entity.Product;
import com.petshop.entity.Role;
import com.petshop.entity.User;
import com.petshop.repository.BrandRepository;
import com.petshop.repository.CategoryRepository;
import com.petshop.repository.ProductRepository;
import com.petshop.repository.RoleRepository;
import com.petshop.repository.UserRepository;
import com.petshop.service.BrandManageService;
import com.petshop.service.EmailService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:brandlogos;DB_CLOSE_DELAY=-1;MODE=MSSQLServer;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class BrandLogoWorkflowTests {
    @Autowired MockMvc mvc;
    @Autowired BrandManageService service;
    @Autowired BrandRepository brands;
    @Autowired ProductRepository products;
    @Autowired CategoryRepository categories;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PlatformTransactionManager transactions;
    @MockBean EmailService mail;
    private final Set<Path> createdFiles = new HashSet<>();

    @AfterEach
    void cleanOnlyFixtureFiles() throws Exception {
        for (Path path : createdFiles) Files.deleteIfExists(path);
    }

    private BrandFormDto form(String name) {
        var dto = new BrandFormDto();
        dto.setName(name);
        dto.setDescription("Description");
        return dto;
    }

    private MockMultipartFile picture(int width) throws Exception {
        var image = new BufferedImage(width, 2, BufferedImage.TYPE_INT_RGB);
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return new MockMultipartFile("logoFile", "../../untrusted.png", "image/png", bytes.toByteArray());
    }

    private Path logo(Brand brand) {
        Path path = Path.of(brand.getLogoUrl().substring(1));
        createdFiles.add(path);
        return path;
    }

    private Brand createWithLogo() throws Exception {
        Brand brand = service.save(null, form(UUID.randomUUID().toString()), picture(3), false);
        logo(brand);
        return brand;
    }

    @Test void multipartCreateAndUpdateUseUploadedLogoAndIgnoreForgedUrl() throws Exception {
        String name = "Multipart " + UUID.randomUUID();
        mvc.perform(multipart("/admin/brands/create").file(picture(6))
                .param("name", name).param("logoUrl", "https://untrusted.test/logo.png")
                .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/brands"));
        Brand created = brands.findAll().stream().filter(b -> name.equals(b.getName())).findFirst().orElseThrow();
        Path previous = logo(created);
        mvc.perform(multipart("/admin/brands/update/" + created.getId()).file(picture(8))
                .param("name", name).with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/brands"));
        assertFalse(Files.exists(previous));
        assertEquals(8, ImageIO.read(logo(brands.findById(created.getId()).orElseThrow()).toFile()).getWidth());
    }

    @Test void invalidMultipartUpdateReopensModalWithPreviousLogoAndSubmittedText() throws Exception {
        Brand created = createWithLogo();
        var invalid = new MockMultipartFile("logoFile", "fake.png", "image/png", "invalid".getBytes());
        mvc.perform(multipart("/admin/brands/update/" + created.getId()).file(invalid)
                .param("name", "Keep this input").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk()).andExpect(model().attribute("brandModalOpen", true))
                .andExpect(model().attributeHasFieldErrors("brandForm", "logoFile"))
                .andExpect(model().attribute("currentLogoUrl", created.getLogoUrl()))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Keep this input")));
        assertEquals(created.getName(), brands.findById(created.getId()).orElseThrow().getName());
    }

    @Test
    void createStoresAReencodedImageWithGeneratedFilename() throws Exception {
        Brand brand = createWithLogo();
        assertTrue(brand.getLogoUrl().matches("/uploads/brands/brand_[0-9a-f-]{36}\\.png"));
        assertEquals(3, ImageIO.read(logo(brand).toFile()).getWidth());
        assertTrue(brands.findById(brand.getId()).orElseThrow().getIsActive());
    }

    @Test
    void editingWithoutFileKeepsLogoAndExistingStatus() throws Exception {
        Brand brand = createWithLogo();
        service.toggleStatus(brand.getId());
        Brand updated = service.save(brand.getId(), form("Renamed " + UUID.randomUUID()), null, false);
        assertEquals(brand.getLogoUrl(), updated.getLogoUrl());
        assertFalse(updated.getIsActive());
        assertTrue(Files.exists(logo(brand)));
    }

    @Test
    void replacementDeletesPreviousUploadedFileOnlyAfterCommit() throws Exception {
        Brand original = createWithLogo();
        Path oldPath = logo(original);
        var file = picture(5);
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            Brand changed = service.save(original.getId(), form(original.getName()), file, false);
            assertTrue(Files.exists(oldPath));
            assertTrue(Files.exists(logo(changed)));
            assertNotEquals(original.getLogoUrl(), changed.getLogoUrl());
        });
        assertFalse(Files.exists(oldPath));
        assertEquals(5, ImageIO.read(logo(brands.findById(original.getId()).orElseThrow()).toFile()).getWidth());
    }

    @Test
    void failedReplacementRemovesNewFileAndRetainsPreviousLogo() throws Exception {
        Brand original = createWithLogo();
        var file = picture(5);
        Path[] replacement = new Path[1];
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            replacement[0] = logo(service.save(original.getId(), form("Rolled back " + UUID.randomUUID()), file, false));
            tx.setRollbackOnly();
        });
        Brand saved = brands.findById(original.getId()).orElseThrow();
        assertEquals(original.getName(), saved.getName());
        assertEquals(original.getLogoUrl(), saved.getLogoUrl());
        assertTrue(Files.exists(logo(original)));
        assertFalse(Files.exists(replacement[0]));
    }

    @Test
    void removingLogoHonorsRollbackAndCommit() throws Exception {
        Brand original = createWithLogo();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            assertNull(service.save(original.getId(), form(original.getName()), null, true).getLogoUrl());
            assertTrue(Files.exists(logo(original)));
            tx.setRollbackOnly();
        });
        assertEquals(original.getLogoUrl(), brands.findById(original.getId()).orElseThrow().getLogoUrl());
        service.save(original.getId(), form(original.getName()), null, true);
        assertNull(brands.findById(original.getId()).orElseThrow().getLogoUrl());
        assertFalse(Files.exists(logo(original)));
    }

    @Test
    void invalidUploadsAndConflictingInstructionsDoNotPartiallySave() throws Exception {
        Brand original = createWithLogo();
        var tooLarge = new MockMultipartFile("logoFile", "large.png", "image/png", new byte[2 * 1024 * 1024 + 1]);
        var fake = new MockMultipartFile("logoFile", "fake.png", "image/png", "text file".getBytes());
        var wrongMime = new MockMultipartFile("logoFile", "file.svg", "image/svg+xml", "<svg/>".getBytes());
        for (var invalid : new MockMultipartFile[]{tooLarge, fake, wrongMime, picture(4097)}) {
            var error = assertThrows(IllegalArgumentException.class,
                    () -> service.save(original.getId(), form("Invalid edit"), invalid, false));
            assertTrue(error.getMessage().startsWith("Logo"));
        }
        var file = picture(3);
        assertThrows(IllegalArgumentException.class, () -> service.save(original.getId(), form("Conflicting"), file, true));
        Brand saved = brands.findById(original.getId()).orElseThrow();
        assertEquals(original.getName(), saved.getName());
        assertEquals(original.getLogoUrl(), saved.getLogoUrl());
        assertTrue(Files.exists(logo(original)));
    }

    @Test
    void deletePreservesLogoWhenBrandIsStillUsedByAProduct() throws Exception {
        Brand brand = createWithLogo();
        String tag = UUID.randomUUID().toString();
        Role role = roles.findByCode("ADMIN").orElseGet(() -> roles.save(Role.builder().code("ADMIN").name("Admin").build()));
        User author = users.save(User.builder().role(role).fullName("Fixture author").email(tag + "@logo.test").passwordHash("unused").build());
        Category category = categories.save(Category.builder().code(tag).name("Fixture category").build());
        products.saveAndFlush(Product.builder().name("Fixture product").slug(tag).category(category).brand(brand).createdBy(author).build());
        assertThrows(IllegalArgumentException.class, () -> service.delete(brand.getId()));
        assertTrue(brands.existsById(brand.getId()));
        assertTrue(Files.exists(logo(brand)));
        Brand unused = createWithLogo();
        service.delete(unused.getId());
        assertFalse(brands.existsById(unused.getId()));
        assertFalse(Files.exists(logo(unused)));
    }

    @Test
    void seededAndExternalLogoPathsAreNeverDeleted() throws Exception {
        Brand brand = brands.saveAndFlush(Brand.builder().name(UUID.randomUUID().toString())
                .logoUrl("/images/product-placeholder.svg").build());
        service.save(brand.getId(), form(brand.getName()), null, true);
        assertTrue(Files.exists(Path.of("src/main/resources/static/images/product-placeholder.svg")));
        service.delete(brand.getId());
    }
}
