import React, { useState, useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { carCodeApi } from '../api/carCodeApi';

/**
 * CarCodeSelector - 5-level cascading car code selector
 * Supports maker, modelGroup, model, trim, and grade selection
 */
export default function CarCodeSelector({ onChange, showImage = true }) {
  const [maker, setMaker] = useState('');
  const [modelGroup, setModelGroup] = useState('');
  const [model, setModel] = useState('');
  const [trim, setTrim] = useState('');
  const [grade, setGrade] = useState('');
  const [modelImageUrl, setModelImageUrl] = useState('');

  // Query makers
  const { data: makers = [], isLoading: loadingMakers } = useQuery({
    queryKey: ['makers'],
    queryFn: carCodeApi.getMakers,
  });

  // Query model groups (depends on maker)
  const { data: modelGroups = [], isLoading: loadingModelGroups } = useQuery({
    queryKey: ['modelGroups', maker],
    queryFn: () => carCodeApi.getModelGroups(maker),
    enabled: !!maker,
  });

  // Query models (depends on maker and modelGroup)
  const { data: models = [], isLoading: loadingModels } = useQuery({
    queryKey: ['models', maker, modelGroup],
    queryFn: () => carCodeApi.getModels(maker, modelGroup),
    enabled: !!maker && !!modelGroup,
  });

  // Query trims (depends on maker, modelGroup, model)
  const { data: trims = [], isLoading: loadingTrims } = useQuery({
    queryKey: ['trims', maker, modelGroup, model],
    queryFn: () => carCodeApi.getTrims(maker, modelGroup, model),
    enabled: !!maker && !!modelGroup && !!model,
  });

  // Query grades (depends on all previous levels)
  const { data: grades = [], isLoading: loadingGrades } = useQuery({
    queryKey: ['grades', maker, modelGroup, model, trim],
    queryFn: () => carCodeApi.getGrades(maker, modelGroup, model, trim),
    enabled: !!maker && !!modelGroup && !!model && !!trim,
  });

  // Fetch model image when model is selected
  useEffect(() => {
    if (model && showImage) {
      carCodeApi.getModelImage(model).then((data) => {
        setModelImageUrl(data.imageUrl);
      }).catch(() => {
        setModelImageUrl('');
      });
    } else {
      setModelImageUrl('');
    }
  }, [model, showImage]);

  // Notify parent of selection changes
  useEffect(() => {
    if (onChange) {
      onChange({
        maker,
        modelGroup,
        model,
        trim,
        grade,
      });
    }
  }, [maker, modelGroup, model, trim, grade, onChange]);

  // Reset dependent fields when parent changes
  const handleMakerChange = (e) => {
    setMaker(e.target.value);
    setModelGroup('');
    setModel('');
    setTrim('');
    setGrade('');
  };

  const handleModelGroupChange = (e) => {
    setModelGroup(e.target.value);
    setModel('');
    setTrim('');
    setGrade('');
  };

  const handleModelChange = (e) => {
    setModel(e.target.value);
    setTrim('');
    setGrade('');
  };

  const handleTrimChange = (e) => {
    setTrim(e.target.value);
    setGrade('');
  };

  const handleGradeChange = (e) => {
    setGrade(e.target.value);
  };

  return (
    <div className="car-code-selector">
      <div className="selector-grid">
        {/* Maker */}
        <div className="selector-field">
          <label htmlFor="maker">Maker</label>
          <select
            id="maker"
            value={maker}
            onChange={handleMakerChange}
            disabled={loadingMakers}
          >
            <option value="">-- Select Maker --</option>
            {makers.map((m) => (
              <option key={m.code} value={m.code}>
                {m.name}
              </option>
            ))}
          </select>
        </div>

        {/* Model Group */}
        <div className="selector-field">
          <label htmlFor="modelGroup">Model Group</label>
          <select
            id="modelGroup"
            value={modelGroup}
            onChange={handleModelGroupChange}
            disabled={!maker || loadingModelGroups}
          >
            <option value="">-- Select Model Group --</option>
            {modelGroups.map((mg) => (
              <option key={mg.code} value={mg.code}>
                {mg.name}
              </option>
            ))}
          </select>
        </div>

        {/* Model */}
        <div className="selector-field">
          <label htmlFor="model">Model</label>
          <select
            id="model"
            value={model}
            onChange={handleModelChange}
            disabled={!modelGroup || loadingModels}
          >
            <option value="">-- Select Model --</option>
            {models.map((m) => (
              <option key={m.code} value={m.code}>
                {m.name}
              </option>
            ))}
          </select>
        </div>

        {/* Trim */}
        <div className="selector-field">
          <label htmlFor="trim">Trim</label>
          <select
            id="trim"
            value={trim}
            onChange={handleTrimChange}
            disabled={!model || loadingTrims}
          >
            <option value="">-- Select Trim --</option>
            {trims.map((t) => (
              <option key={t.code} value={t.code}>
                {t.name}
              </option>
            ))}
          </select>
        </div>

        {/* Grade */}
        <div className="selector-field">
          <label htmlFor="grade">Grade</label>
          <select
            id="grade"
            value={grade}
            onChange={handleGradeChange}
            disabled={!trim || loadingGrades}
          >
            <option value="">-- Select Grade --</option>
            {grades.map((g) => (
              <option key={g.code} value={g.code}>
                {g.name}
              </option>
            ))}
          </select>
        </div>
      </div>

      {/* Model Image Preview */}
      {showImage && model && modelImageUrl && (
        <div className="model-image-preview">
          <img src={modelImageUrl} alt={`${model} preview`} />
        </div>
      )}

      <style jsx="true">{`
        .car-code-selector {
          margin: 20px 0;
        }

        .selector-grid {
          display: grid;
          grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
          gap: 15px;
          margin-bottom: 20px;
        }

        .selector-field {
          display: flex;
          flex-direction: column;
        }

        .selector-field label {
          font-weight: 600;
          margin-bottom: 5px;
          font-size: 14px;
          color: #333;
        }

        .selector-field select {
          padding: 8px 12px;
          border: 1px solid #ddd;
          border-radius: 4px;
          font-size: 14px;
          background-color: white;
          cursor: pointer;
        }

        .selector-field select:disabled {
          background-color: #f5f5f5;
          cursor: not-allowed;
          color: #999;
        }

        .selector-field select:focus {
          outline: none;
          border-color: #4a90e2;
          box-shadow: 0 0 0 2px rgba(74, 144, 226, 0.1);
        }

        .model-image-preview {
          margin-top: 20px;
          text-align: center;
        }

        .model-image-preview img {
          max-width: 500px;
          max-height: 300px;
          width: 100%;
          height: auto;
          border-radius: 8px;
          box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
        }
      `}</style>
    </div>
  );
}
