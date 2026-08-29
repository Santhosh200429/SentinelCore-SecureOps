package com.santhosh.dashboard.controller;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.santhosh.dashboard.aop.Auditable;
import com.santhosh.dashboard.model.Asset;
import com.santhosh.dashboard.repository.AssetRepository;

@RestController
@RequestMapping("/api/assets")
public class AssetController {

  private final AssetRepository assetRepository;
  @Value("${asset.offline-timeout-seconds:60}")
  private long offlineTimeoutSeconds;

  public AssetController(AssetRepository assetRepository) {
    this.assetRepository = assetRepository;
  }

  // Test API
  @GetMapping("/test")
  public String test() {
    return "Asset Controller Working!";
  }

  // Get all assets
  @GetMapping
  @PreAuthorize("hasAuthority('ASSET_VIEW')")
  public List<Asset> getAllAssets() {
    List<Asset> assets = assetRepository.findAll();
    refreshStaleStatuses(assets);
    return assets;
  }

  // Get asset by ID
  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('ASSET_VIEW')")
  public Optional<Asset> getAssetById(@PathVariable Long id) {
    Optional<Asset> asset = assetRepository.findById(id);
    asset.ifPresent(this::refreshStatus);
    return asset;
  }

  // Create a new asset
  @PostMapping
  @PreAuthorize("hasAuthority('ASSET_CREATE')")
  @Auditable(action = "Create Asset")
  public Asset createAsset(@RequestBody Asset asset) {
    // Validate IP address uniqueness
    if (assetRepository.existsByIpAddress(asset.getIpAddress())) {
      throw new RuntimeException("IP Address already assigned to another asset.");
    }
    return assetRepository.save(asset);
  }

  // Update an existing asset
  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('ASSET_EDIT')")
  @Auditable(action = "Edit Asset")
  public Asset updateAsset(@PathVariable Long id,
                           @RequestBody Asset updatedAsset) {

    return assetRepository.findById(id)
      .map(asset -> {

        asset.setAssetName(updatedAsset.getAssetName());
        asset.setIpAddress(updatedAsset.getIpAddress());
        asset.setAssetType(updatedAsset.getAssetType());
        asset.setStatus(updatedAsset.getStatus());

        asset.setCpuUsage(updatedAsset.getCpuUsage());
        asset.setMemoryUsage(updatedAsset.getMemoryUsage());
        asset.setDiskUsage(updatedAsset.getDiskUsage());
        asset.setNetworkUsage(updatedAsset.getNetworkUsage());

        asset.setUptime(updatedAsset.getUptime());
        asset.setLocation(updatedAsset.getLocation());

        // Validate IP address uniqueness (excluding current asset)
        if (assetRepository.existsByIpAddressAndIdNot(updatedAsset.getIpAddress(), id)) {
          throw new RuntimeException("IP Address already assigned to another asset.");
        }

        return assetRepository.save(asset);

      })
      .orElseThrow(() -> new RuntimeException("Asset not found"));
  }

  // Delete an asset
  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('ASSET_DELETE')")
  @Auditable(action = "Delete Asset")
  public String deleteAsset(@PathVariable Long id) {
    assetRepository.deleteById(id);
    return "Asset deleted successfully!";
  }
  @GetMapping("/search")
  @PreAuthorize("hasAuthority('ASSET_VIEW')")
  public List<Asset> searchAssets(@RequestParam String keyword) {

    return assetRepository.findByAssetNameContainingIgnoreCase(keyword);

  }
  @GetMapping("/status/{status}")
  @PreAuthorize("hasAuthority('ASSET_VIEW')")
  public List<Asset> getAssetsByStatus(@PathVariable String status) {

    return assetRepository.findByStatus(status);

  }
  private void refreshStaleStatuses(List<Asset> assets) { assets.forEach(this::refreshStatus); }
  private void refreshStatus(Asset asset) {
    if (asset.getLastSeenEpoch() == null || asset.getMachineId() == null) return;
    if ("MAINTENANCE".equalsIgnoreCase(asset.getStatus())) return;
    long age = System.currentTimeMillis() - asset.getLastSeenEpoch();
    if (age > offlineTimeoutSeconds * 1000L) asset.setStatus("OFFLINE"); else asset.setStatus("ONLINE");
    assetRepository.save(asset);
  }

}
