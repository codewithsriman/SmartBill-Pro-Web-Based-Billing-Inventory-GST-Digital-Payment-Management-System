package com.smartbillpro.backend.service;

import com.smartbillpro.backend.dto.product.ProductRequest;
import com.smartbillpro.backend.dto.product.ProductResponse;
import com.smartbillpro.backend.entity.Category;
import com.smartbillpro.backend.entity.Product;
import com.smartbillpro.backend.exception.DuplicateResourceException;
import com.smartbillpro.backend.exception.ResourceNotFoundException;
import com.smartbillpro.backend.repository.CategoryRepository;
import com.smartbillpro.backend.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final QrCodeService qrCodeService;

    @Transactional
    public ProductResponse createProduct(ProductRequest request, Long createdByUserId) {
        String barcode = (request.getBarcode() == null || request.getBarcode().isBlank())
                ? generateBarcode()
                : request.getBarcode();

        if (productRepository.existsByBarcode(barcode)) {
            throw new DuplicateResourceException("A product with barcode '" + barcode + "' already exists");
        }

        Category category = resolveCategory(request.getCategoryId());

        Product product = Product.builder()
                .productName(request.getProductName())
                .barcode(barcode)
                .category(category)
                .unit(request.getUnit())
                .price(request.getPrice())
                .gstPercentage(request.getGstPercentage())
                .stockQuantity(request.getStockQuantity())
                .reorderLevel(request.getReorderLevel() == null ? BigDecimal.ZERO : request.getReorderLevel())
                .createdBy(createdByUserId)
                .build();

        // QR payload encodes the barcode so the scanner module can resolve it back to this product
        String qrPayload = "SMARTBILL:" + barcode;
        product.setQrCodeData(qrPayload);

        Product saved = productRepository.save(product);

        String qrImagePath = qrCodeService.generateAndSave(qrPayload, "product_" + saved.getId());
        saved.setQrCodeImagePath(qrImagePath);
        saved = productRepository.save(saved);

        return toResponse(saved);
    }

    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));

        if (request.getBarcode() != null && !request.getBarcode().isBlank()
                && !request.getBarcode().equals(product.getBarcode())
                && productRepository.existsByBarcode(request.getBarcode())) {
            throw new DuplicateResourceException("A product with barcode '" + request.getBarcode() + "' already exists");
        }

        product.setProductName(request.getProductName());
        if (request.getBarcode() != null && !request.getBarcode().isBlank()) {
            product.setBarcode(request.getBarcode());
        }
        product.setCategory(resolveCategory(request.getCategoryId()));
        product.setUnit(request.getUnit());
        product.setPrice(request.getPrice());
        product.setGstPercentage(request.getGstPercentage());
        product.setStockQuantity(request.getStockQuantity());
        product.setReorderLevel(request.getReorderLevel() == null ? BigDecimal.ZERO : request.getReorderLevel());

        return toResponse(productRepository.save(product));
    }

    @Transactional
    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
        product.setIsActive(false); // soft delete to preserve invoice history integrity
        productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public ProductResponse getProduct(Long id) {
        return toResponse(productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id)));
    }

    @Transactional(readOnly = true)
    public ProductResponse getByBarcodeOrQr(String code) {
        // Try direct barcode match first, then QR payload match (scanner sends raw scanned text)
        return productRepository.findByBarcode(code)
                .or(() -> productRepository.findByQrCodeData(code))
                .or(() -> productRepository.findByQrCodeData("SMARTBILL:" + code))
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "barcode/QR", code));
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> searchProducts(String keyword, Long categoryId, Pageable pageable) {
        Page<Product> page;
        if (categoryId != null) {
            page = productRepository.findByCategoryId(categoryId, pageable);
        } else if (keyword != null && !keyword.isBlank()) {
            page = productRepository.search(keyword, pageable);
        } else {
            page = productRepository.findAll(pageable);
        }
        return page.map(this::toResponse);
    }

    private Category resolveCategory(Long categoryId) {
        if (categoryId == null) return null;
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", categoryId));
    }

    private String generateBarcode() {
        // Simple, collision-resistant default; real deployments may prefer sequential EAN-13s
        String candidate;
        do {
            candidate = "SB" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        } while (productRepository.existsByBarcode(candidate));
        return candidate;
    }

    private ProductResponse toResponse(Product p) {
        boolean lowStock = p.getReorderLevel() != null
                && p.getStockQuantity().compareTo(p.getReorderLevel()) <= 0;

        return ProductResponse.builder()
                .id(p.getId())
                .productName(p.getProductName())
                .barcode(p.getBarcode())
                .qrCodeData(p.getQrCodeData())
                .qrCodeImageUrl(p.getQrCodeImagePath() != null ? "/uploads/" + p.getQrCodeImagePath() : null)
                .categoryId(p.getCategory() != null ? p.getCategory().getId() : null)
                .categoryName(p.getCategory() != null ? p.getCategory().getName() : null)
                .unit(p.getUnit())
                .price(p.getPrice())
                .gstPercentage(p.getGstPercentage())
                .stockQuantity(p.getStockQuantity())
                .reorderLevel(p.getReorderLevel())
                .lowStock(lowStock)
                .active(Boolean.TRUE.equals(p.getIsActive()))
                .build();
    }
}
