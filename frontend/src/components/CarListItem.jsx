import React, { useState, useEffect } from 'react';
import { carCodeApi } from '../api/carCodeApi';

/**
 * CarListItem - Display a car with representative model image
 */
export default function CarListItem({ car }) {
  const [imageUrl, setImageUrl] = useState('');

  useEffect(() => {
    if (car.modelCode) {
      carCodeApi.getModelImage(car.modelCode)
        .then((data) => setImageUrl(data.imageUrl))
        .catch(() => setImageUrl(''));
    }
  }, [car.modelCode]);

  return (
    <div className="car-list-item">
      {imageUrl && (
        <div className="car-image">
          <img src={imageUrl} alt={car.modelName || 'Car'} />
        </div>
      )}
      <div className="car-details">
        <h3>{car.makerName} {car.modelName}</h3>
        {car.trimName && <p className="trim">{car.trimName}</p>}
        <div className="car-meta">
          {car.year && <span>Year: {car.year}</span>}
          {car.mileage && <span>Mileage: {car.mileage.toLocaleString()} km</span>}
          {car.color && <span>Color: {car.color}</span>}
        </div>
      </div>

      <style jsx="true">{`
        .car-list-item {
          display: flex;
          gap: 15px;
          padding: 15px;
          border: 1px solid #e0e0e0;
          border-radius: 8px;
          background: white;
          margin-bottom: 15px;
        }

        .car-image {
          flex-shrink: 0;
          width: 200px;
          height: 120px;
          overflow: hidden;
          border-radius: 4px;
        }

        .car-image img {
          width: 100%;
          height: 100%;
          object-fit: cover;
        }

        .car-details {
          flex: 1;
        }

        .car-details h3 {
          margin: 0 0 8px 0;
          font-size: 18px;
          font-weight: 600;
          color: #333;
        }

        .car-details .trim {
          margin: 0 0 10px 0;
          font-size: 14px;
          color: #666;
        }

        .car-meta {
          display: flex;
          gap: 15px;
          font-size: 13px;
          color: #888;
        }

        .car-meta span {
          display: inline-block;
        }
      `}</style>
    </div>
  );
}
