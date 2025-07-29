package kr.hhplus.be.server.unit.adapter.storage.jpa.product;

import kr.hhplus.be.server.TestcontainersConfiguration;
import kr.hhplus.be.server.adapter.storage.jpa.ProductJpaRepository;
import kr.hhplus.be.server.domain.entity.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductJpaRepository 단위 테스트")
class ProductJpaRepositoryTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private TypedQuery<Product> productQuery;

    private ProductJpaRepository productJpaRepository;

    @BeforeEach
    void setUp() {
        productJpaRepository = new ProductJpaRepository(entityManager);
    }

    @Nested
    @DisplayName("상품 저장 테스트")
    class SaveTests {

        @Test
        @DisplayName("성공케이스: 새로운 상품 저장")
        void save_NewProduct_Success() {
            // given
            Product product = Product.builder()
                    .name("테스트 상품")
                    .price(new BigDecimal("10000"))
                    .stock(100)
                    .createdAt(LocalDateTime.now())
                    .build();

            doNothing().when(entityManager).persist(product);

            // when
            Product savedProduct = productJpaRepository.save(product);

            // then
            assertThat(savedProduct).isEqualTo(product);
            verify(entityManager, times(1)).persist(product);
            verify(entityManager, never()).merge(any());
        }

        @Test
        @DisplayName("성공케이스: 기존 상품 업데이트")
        void save_ExistingProduct_Success() {
            // given
            Product product = Product.builder()
                    .id(1L)
                    .name("업데이트된 상품")
                    .price(new BigDecimal("15000"))
                    .stock(50)
                    .build();

            when(entityManager.merge(product)).thenReturn(product);

            // when
            Product savedProduct = productJpaRepository.save(product);

            // then
            assertThat(savedProduct).isEqualTo(product);
            verify(entityManager, times(1)).merge(product);
            verify(entityManager, never()).persist(any());
        }

        @ParameterizedTest
        @ValueSource(ints = {0, 1, 10, 100, 1000})
        @DisplayName("성공케이스: 다양한 재고 수량으로 저장")
        void save_WithDifferentStockQuantities(int stock) {
            // given
            Product product = Product.builder()
                    .name("테스트 상품")
                    .price(new BigDecimal("10000"))
                    .stock(stock)
                    .build();

            doNothing().when(entityManager).persist(product);

            // when
            Product savedProduct = productJpaRepository.save(product);

            // then
            assertThat(savedProduct.getStock()).isEqualTo(stock);
            verify(entityManager, times(1)).persist(product);
        }
    }

    @Nested
    @DisplayName("ID로 조회 테스트")
    class FindByIdTests {

        @Test
        @DisplayName("성공케이스: ID로 상품 조회")
        void findById_Success() {
            // given
            Long id = 1L;
            Product expectedProduct = Product.builder()
                    .id(id)
                    .name("테스트 상품")
                    .price(new BigDecimal("10000"))
                    .stock(100)
                    .build();

            when(entityManager.find(Product.class, id)).thenReturn(expectedProduct);

            // when
            Optional<Product> foundProduct = productJpaRepository.findById(id);

            // then
            assertThat(foundProduct).isPresent();
            assertThat(foundProduct.get()).isEqualTo(expectedProduct);
        }

        @Test
        @DisplayName("실패케이스: 존재하지 않는 ID로 조회")
        void findById_NotFound() {
            // given
            Long id = 999L;
            when(entityManager.find(Product.class, id)).thenReturn(null);

            // when
            Optional<Product> foundProduct = productJpaRepository.findById(id);

            // then
            assertThat(foundProduct).isEmpty();
        }

        @Test
        @DisplayName("실패케이스: 조회 중 예외 발생")
        void findById_Exception() {
            // given
            Long id = 1L;
            when(entityManager.find(Product.class, id))
                    .thenThrow(new RuntimeException("데이터베이스 오류"));

            // when
            Optional<Product> result = productJpaRepository.findById(id);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("인기 상품 조회 테스트")
    class FindPopularProductsTests {

        @Test
        @DisplayName("성공케이스: 인기 상품 조회")
        void findPopularProducts_Success() {
            // given
            int period = 7;
            List<Product> expectedProducts = Arrays.asList(
                    Product.builder().id(1L).name("인기상품1").build(),
                    Product.builder().id(2L).name("인기상품2").build(),
                    Product.builder().id(3L).name("인기상품3").build()
            );

            when(entityManager.createQuery(anyString(), eq(Product.class))).thenReturn(productQuery);
            when(productQuery.setParameter(eq("periodDate"), any(LocalDateTime.class))).thenReturn(productQuery);
            when(productQuery.getResultList()).thenReturn(expectedProducts);

            // when
            List<Product> products = productJpaRepository.findPopularProducts(period);

            // then
            assertThat(products).hasSize(3);
            verify(entityManager).createQuery("SELECT p FROM Product p WHERE p.createdAt >= :periodDate ORDER BY p.createdAt DESC", Product.class);
        }

        @Test
        @DisplayName("성공케이스: 인기 상품이 없는 경우")
        void findPopularProducts_EmptyResult() {
            // given
            int period = 30;

            when(entityManager.createQuery(anyString(), eq(Product.class))).thenReturn(productQuery);
            when(productQuery.setParameter(eq("periodDate"), any(LocalDateTime.class))).thenReturn(productQuery);
            when(productQuery.getResultList()).thenReturn(Arrays.asList());

            // when
            List<Product> products = productJpaRepository.findPopularProducts(period);

            // then
            assertThat(products).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 7, 30, 90, 365})
        @DisplayName("성공케이스: 다양한 기간으로 인기 상품 조회")
        void findPopularProducts_WithDifferentPeriods(int period) {
            // given
            List<Product> expectedProducts = Arrays.asList(
                    Product.builder().id(1L).build()
            );

            when(entityManager.createQuery(anyString(), eq(Product.class))).thenReturn(productQuery);
            when(productQuery.setParameter(eq("periodDate"), any(LocalDateTime.class))).thenReturn(productQuery);
            when(productQuery.getResultList()).thenReturn(expectedProducts);

            // when
            List<Product> products = productJpaRepository.findPopularProducts(period);

            // then
            assertThat(products).hasSize(1);
            verify(productQuery).setParameter(eq("periodDate"), any(LocalDateTime.class));
        }
    }

    @Nested
    @DisplayName("페이징 조회 테스트")
    class FindAllWithPaginationTests {

        @Test
        @DisplayName("성공케이스: 페이징으로 상품 조회")
        void findAllWithPagination_Success() {
            // given
            int limit = 10;
            int offset = 0;
            List<Product> expectedProducts = Arrays.asList(
                    Product.builder().id(3L).name("최신상품").createdAt(LocalDateTime.now()).build(),
                    Product.builder().id(2L).name("이전상품").createdAt(LocalDateTime.now().minusDays(1)).build(),
                    Product.builder().id(1L).name("오래된상품").createdAt(LocalDateTime.now().minusDays(2)).build()
            );

            when(entityManager.createQuery(anyString(), eq(Product.class))).thenReturn(productQuery);
            when(productQuery.setMaxResults(limit)).thenReturn(productQuery);
            when(productQuery.setFirstResult(offset)).thenReturn(productQuery);
            when(productQuery.getResultList()).thenReturn(expectedProducts);

            // when
            List<Product> products = productJpaRepository.findAllWithPagination(limit, offset);

            // then
            assertThat(products).hasSize(3);
            verify(entityManager).createQuery("SELECT p FROM Product p ORDER BY p.createdAt DESC", Product.class);
            verify(productQuery).setMaxResults(limit);
            verify(productQuery).setFirstResult(offset);
        }

        @Test
        @DisplayName("성공케이스: 두 번째 페이지 조회")
        void findAllWithPagination_SecondPage() {
            // given
            int limit = 5;
            int offset = 5;
            List<Product> expectedProducts = Arrays.asList(
                    Product.builder().id(5L).name("상품5").build(),
                    Product.builder().id(4L).name("상품4").build()
            );

            when(entityManager.createQuery(anyString(), eq(Product.class))).thenReturn(productQuery);
            when(productQuery.setMaxResults(limit)).thenReturn(productQuery);
            when(productQuery.setFirstResult(offset)).thenReturn(productQuery);
            when(productQuery.getResultList()).thenReturn(expectedProducts);

            // when
            List<Product> products = productJpaRepository.findAllWithPagination(limit, offset);

            // then
            assertThat(products).hasSize(2);
            verify(productQuery).setMaxResults(limit);
            verify(productQuery).setFirstResult(offset);
        }

        @Test
        @DisplayName("성공케이스: 빈 페이지 조회")
        void findAllWithPagination_EmptyPage() {
            // given
            int limit = 10;
            int offset = 100;

            when(entityManager.createQuery(anyString(), eq(Product.class))).thenReturn(productQuery);
            when(productQuery.setMaxResults(limit)).thenReturn(productQuery);
            when(productQuery.setFirstResult(offset)).thenReturn(productQuery);
            when(productQuery.getResultList()).thenReturn(Arrays.asList());

            // when
            List<Product> products = productJpaRepository.findAllWithPagination(limit, offset);

            // then
            assertThat(products).isEmpty();
        }
    }

    @Nested
    @DisplayName("예외 상황 테스트")
    class ExceptionTests {

        @Test
        @DisplayName("실패케이스: persist 중 예외 발생")
        void save_PersistException() {
            // given
            Product product = Product.builder()
                    .name("테스트 상품")
                    .price(new BigDecimal("10000"))
                    .stock(100)
                    .build();

            doThrow(new RuntimeException("DB 연결 실패")).when(entityManager).persist(product);

            // when & then
            assertThatThrownBy(() -> productJpaRepository.save(product))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("DB 연결 실패");
        }

        @Test
        @DisplayName("실패케이스: merge 중 예외 발생")
        void save_MergeException() {
            // given
            Product product = Product.builder()
                    .id(1L)
                    .name("테스트 상품")
                    .price(new BigDecimal("10000"))
                    .build();

            when(entityManager.merge(product)).thenThrow(new RuntimeException("트랜잭션 오류"));

            // when & then
            assertThatThrownBy(() -> productJpaRepository.save(product))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("트랜잭션 오류");
        }

        @Test
        @DisplayName("실패케이스: 인기 상품 조회 중 예외 발생")
        void findPopularProducts_QueryException() {
            // given
            int period = 7;

            when(entityManager.createQuery(anyString(), eq(Product.class)))
                    .thenThrow(new RuntimeException("쿼리 실행 오류"));

            // when & then
            assertThatThrownBy(() -> productJpaRepository.findPopularProducts(period))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("쿼리 실행 오류");
        }

        @Test
        @DisplayName("실패케이스: 페이징 조회 중 예외 발생")
        void findAllWithPagination_QueryException() {
            // given
            int limit = 10;
            int offset = 0;

            when(entityManager.createQuery(anyString(), eq(Product.class)))
                    .thenThrow(new RuntimeException("데이터베이스 연결 오류"));

            // when & then
            assertThatThrownBy(() -> productJpaRepository.findAllWithPagination(limit, offset))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("데이터베이스 연결 오류");
        }
    }
}