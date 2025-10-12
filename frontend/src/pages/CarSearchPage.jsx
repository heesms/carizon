import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import CarCodeSelector from '../components/CarCodeSelector';
import CarListItem from '../components/CarListItem';
import { carsApi } from '../api/carsApi';

/**
 * CarSearchPage - Search and filter cars using 5-level code selector
 */
export default function CarSearchPage() {
  const [selectedCodes, setSelectedCodes] = useState({
    maker: '',
    modelGroup: '',
    model: '',
    trim: '',
    grade: '',
  });

  // Query cars based on selected filters
  const { data: carsData, isLoading, error } = useQuery({
    queryKey: ['cars', selectedCodes],
    queryFn: () => {
      const params = {
        page: 0,
        size: 20,
      };
      if (selectedCodes.maker) params.maker = selectedCodes.maker;
      if (selectedCodes.modelGroup) params.modelGroup = selectedCodes.modelGroup;
      if (selectedCodes.model) params.model = selectedCodes.model;
      if (selectedCodes.trim) params.trim = selectedCodes.trim;
      if (selectedCodes.grade) params.grade = selectedCodes.grade;

      return carsApi.getCars(params);
    },
  });

  const handleCodeChange = (codes) => {
    setSelectedCodes(codes);
  };

  return (
    <div className="car-search-page">
      <div className="container">
        <h1>Car Search</h1>

        {/* Car Code Selector */}
        <section className="filter-section">
          <h2>Filter by Car Code</h2>
          <CarCodeSelector onChange={handleCodeChange} showImage={true} />
        </section>

        {/* Selected Filter State */}
        <section className="selected-filters">
          <h3>Selected Filters:</h3>
          <div className="filter-tags">
            {selectedCodes.maker && (
              <span className="filter-tag">Maker: {selectedCodes.maker}</span>
            )}
            {selectedCodes.modelGroup && (
              <span className="filter-tag">Model Group: {selectedCodes.modelGroup}</span>
            )}
            {selectedCodes.model && (
              <span className="filter-tag">Model: {selectedCodes.model}</span>
            )}
            {selectedCodes.trim && (
              <span className="filter-tag">Trim: {selectedCodes.trim}</span>
            )}
            {selectedCodes.grade && (
              <span className="filter-tag">Grade: {selectedCodes.grade}</span>
            )}
            {!selectedCodes.maker && <p className="no-filters">No filters selected</p>}
          </div>
        </section>

        {/* Search Results */}
        <section className="results-section">
          <h2>Results</h2>
          
          {isLoading && <p>Loading...</p>}
          
          {error && (
            <div className="error-message">
              Error loading cars: {error.message}
            </div>
          )}
          
          {carsData && (
            <>
              <p className="results-count">
                Found {carsData.totalElements || 0} cars
                {carsData.content && ` (showing ${carsData.content.length})`}
              </p>
              
              <div className="car-list">
                {carsData.content && carsData.content.length > 0 ? (
                  carsData.content.map((car) => (
                    <CarListItem key={car.carId} car={car} />
                  ))
                ) : (
                  <p className="no-results">No cars found matching the selected filters.</p>
                )}
              </div>
            </>
          )}
        </section>
      </div>

      <style jsx="true">{`
        .car-search-page {
          min-height: 100vh;
          background-color: #f5f5f5;
          padding: 20px 0;
        }

        .container {
          max-width: 1200px;
          margin: 0 auto;
          padding: 0 20px;
        }

        h1 {
          font-size: 32px;
          font-weight: 700;
          color: #333;
          margin-bottom: 30px;
        }

        h2 {
          font-size: 24px;
          font-weight: 600;
          color: #444;
          margin-bottom: 15px;
        }

        h3 {
          font-size: 18px;
          font-weight: 600;
          color: #555;
          margin-bottom: 10px;
        }

        .filter-section {
          background: white;
          padding: 25px;
          border-radius: 8px;
          margin-bottom: 20px;
          box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
        }

        .selected-filters {
          background: white;
          padding: 20px;
          border-radius: 8px;
          margin-bottom: 20px;
          box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
        }

        .filter-tags {
          display: flex;
          flex-wrap: wrap;
          gap: 10px;
        }

        .filter-tag {
          display: inline-block;
          padding: 6px 12px;
          background-color: #e3f2fd;
          color: #1976d2;
          border-radius: 16px;
          font-size: 14px;
          font-weight: 500;
        }

        .no-filters {
          color: #999;
          font-style: italic;
        }

        .results-section {
          background: white;
          padding: 25px;
          border-radius: 8px;
          box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
        }

        .results-count {
          margin-bottom: 20px;
          font-size: 16px;
          color: #666;
        }

        .car-list {
          margin-top: 20px;
        }

        .no-results {
          padding: 40px 20px;
          text-align: center;
          color: #999;
          font-size: 16px;
        }

        .error-message {
          padding: 15px;
          background-color: #ffebee;
          color: #c62828;
          border-radius: 4px;
          margin-bottom: 20px;
        }
      `}</style>
    </div>
  );
}
