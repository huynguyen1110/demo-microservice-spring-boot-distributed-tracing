package com.example.demo;

import java.util.ArrayList;
import java.util.List;

import com.example.demo.model.Product;
import com.example.demo.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RefreshScope
@RestController
public class ProductController {

	private final WebClient webClient;

	@Autowired
	private final ProductRepository productRepository;
	
	public ProductController(WebClient.Builder webClientBuilder, ProductRepository productRepository) {
		this.webClient = webClientBuilder.build();
        this.productRepository = productRepository;
    }

	List<ProductInfo> productList = new ArrayList<ProductInfo>();
	private Logger logger = LoggerFactory.getLogger(ProductController.class);

	@Autowired
	private RestTemplate restTemplate;

	@GetMapping("/product/details/{productid}")
	public Mono<Product> getProductDetails(@PathVariable Long productid) {
		logger.info("Get product details");

		Product product = productRepository.findProductByProductID(productid);

		// Lấy thông tin giá từ pricing-service
		Mono<Price> priceMono = webClient.get()
				.uri("http://localhost:8002/price/{productid}", productid)
				.retrieve()
				.bodyToMono(Price.class);

		// Lấy thông tin kho hàng từ inventory-service
		Mono<Inventory> inventoryMono = webClient.get()
				.uri("http://localhost:8003/inventory/{productid}", productid)
				.retrieve()
				.bodyToMono(Inventory.class);

		// Kết hợp thông tin giá và kho hàng bằng Mono.zip
		return Mono.zip(priceMono, inventoryMono)
				.map(tuple -> {
					Price price = tuple.getT1();
					Inventory inventory = tuple.getT2();
					// Tạo đối tượng Product từ thông tin giá và kho hàng
					return new Product(productid, product.getProductName(), product.getProductDesc(), price.getDiscountedPrice(), inventory.getInStock());
				});
	}


	@GetMapping("/product/list")
	public Flux<Product> getProducts() {

		return Flux.fromStream(productList.stream()).flatMap(productInfo -> {

			// Get Price from pricing-service
			Mono<Price> price = webClient.get()
					.uri("http://localhost:8002/price/101L", productInfo.getProductID()).retrieve()
					.bodyToMono(Price.class);

			// Get Stock Avail from inventory-service

			Mono<Inventory> inventory = webClient.get()
					.uri("http://localhost:8003/inventory/101L", productInfo.getProductID()).retrieve()
					.bodyToMono(Inventory.class);

			return Mono.zip(price, inventory)
					.map(tuple -> new Product(productInfo.getProductID(), productInfo.getProductName(),
							productInfo.getProductDesc(), tuple.getT1().getDiscountedPrice(),
							tuple.getT2().getInStock()));

		});
	}

	private void populateProductList() {
		productList.clear();
		productList.add(new ProductInfo(101L, "iPhone", "iPhone is damn expensive!"));
		productList.add(new ProductInfo(102L, "Book", "Book is great!"));
		productList.add(new ProductInfo(103L, "Washing MC", "Washing MC is necessary"));
	}

}
