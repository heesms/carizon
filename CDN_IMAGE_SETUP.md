# Car Model Images - CDN Setup

This document describes how car model images are served via jsDelivr CDN.

## CDN URL Format

Model images are served from the GitHub repository using jsDelivr CDN:

```
https://cdn.jsdelivr.net/gh/heesms/carizon@main/resource/image/car/model/{MODEL_CODE}/{image_file}
```

### Example URLs

- Avante CN7: `https://cdn.jsdelivr.net/gh/heesms/carizon@main/resource/image/car/model/CN7/avante_cn7_main.jpg`
- Sonata DN8: `https://cdn.jsdelivr.net/gh/heesms/carizon@main/resource/image/car/model/DN8/sonata_dn8_main.jpg`

## Database Structure

The `cz_model_image` table stores model image metadata:

```sql
CREATE TABLE cz_model_image (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  model_code   VARCHAR(64) NOT NULL,
  image_url    VARCHAR(1024) NOT NULL,
  sort_order   INT NOT NULL DEFAULT 0,
  is_main      TINYINT(1) NOT NULL DEFAULT 0,
  created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uq_model_image UNIQUE (model_code, image_url)
);
```

### Fields

- **model_code**: The car model code (e.g., 'CN7', 'DN8', 'K5_DL3')
- **image_url**: Full CDN URL to the image
- **sort_order**: Display order (lower numbers first)
- **is_main**: Main/representative image flag (1 = main, 0 = additional)

## API Endpoints

### Get Representative Image

Returns the main image for a model, or default fallback if none exists.

```
GET /api/code/model-image?modelCode=CN7
```

Response:
```json
{
  "modelCode": "CN7",
  "imageUrl": "https://cdn.jsdelivr.net/gh/heesms/carizon@main/resource/image/car/model/CN7/avante_cn7_main.jpg"
}
```

### Get All Images

Returns all images for a model, ordered by is_main DESC, sort_order ASC.

```
GET /api/code/model-images?modelCode=CN7
```

Response:
```json
[
  {
    "id": 1,
    "modelCode": "CN7",
    "imageUrl": "https://cdn.jsdelivr.net/gh/heesms/carizon@main/resource/image/car/model/CN7/avante_cn7_main.jpg",
    "sortOrder": 0,
    "isMain": true
  },
  {
    "id": 2,
    "modelCode": "CN7",
    "imageUrl": "https://cdn.jsdelivr.net/gh/heesms/carizon@main/resource/image/car/model/CN7/avante_cn7_side.jpg",
    "sortOrder": 1,
    "isMain": false
  }
]
```

## Image Selection Logic

The `CzModelImageService` selects representative images using this priority:

1. **is_main = 1** (main images)
2. **sort_order ASC** (lowest order first)
3. **id ASC** (deterministic fallback)

If no images exist for a model_code, returns the default fallback:
```
https://cdn.jsdelivr.net/gh/heesms/carizon@main/resource/image/car/model/CN7/avante_cn7_main.jpg
```

## Adding New Model Images

To add images for a new car model:

1. **Upload images** to `resource/image/car/model/{MODEL_CODE}/` in the repository
2. **Insert metadata** into `cz_model_image` table:

```sql
INSERT INTO cz_model_image (model_code, image_url, sort_order, is_main) VALUES
('K5_DL3', 'https://cdn.jsdelivr.net/gh/heesms/carizon@main/resource/image/car/model/K5_DL3/k5_dl3_main.jpg', 0, 1),
('K5_DL3', 'https://cdn.jsdelivr.net/gh/heesms/carizon@main/resource/image/car/model/K5_DL3/k5_dl3_side.jpg', 1, 0),
('K5_DL3', 'https://cdn.jsdelivr.net/gh/heesms/carizon@main/resource/image/car/model/K5_DL3/k5_dl3_interior.jpg', 2, 0);
```

## Frontend Usage

The `CarCodeSelector` and `CarListItem` components automatically fetch and display model images:

```jsx
import { carCodeApi } from '../api/carCodeApi';

// Get representative image
const imageData = await carCodeApi.getModelImage('CN7');
console.log(imageData.imageUrl); // CDN URL

// Get all images
const images = await carCodeApi.getModelImages('CN7');
```

## Benefits of jsDelivr CDN

- ✅ **Free** for open source projects
- ✅ **Global CDN** with fast delivery
- ✅ **Version pinning** via `@main` or `@v1.0.0`
- ✅ **No server setup** required
- ✅ **Automatic caching** and compression
- ✅ **GitHub integration** - images update with repository

## Notes

- Image URLs use `@main` branch by default
- For production, consider pinning to specific version tags (e.g., `@v1.0.0`)
- Supported image formats: JPG, PNG, WebP
- Recommended image dimensions: 800x600 or similar aspect ratio
- Keep individual images under 500KB for optimal loading
