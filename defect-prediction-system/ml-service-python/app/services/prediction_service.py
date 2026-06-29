import pandas as pd
from typing import List
from fastapi import UploadFile

from app.services.preprocessing_service import PreprocessingService
from app.services.coral_service import CoralService
from app.services.knn_service import KnnService


class PredictionService:
    def __init__(self):
        self.preprocessor = PreprocessingService()
        self.coral = CoralService()
        self.knn = KnnService()

    async def run(self, target_file: UploadFile, source_files: List[UploadFile],
                  label_column: str, knn_value: int, coral_option: bool):
        # 1. Load target CSV
        target_df = pd.read_csv(target_file.file)

        # 2. Load and merge source CSVs
        source_dfs = []
        for f in source_files:
            source_dfs.append(pd.read_csv(f.file))
        source_df = pd.concat(source_dfs, ignore_index=True)

        # 3. Get common feature columns
        name_col = "name"
        feature_cols = [c for c in target_df.columns if c != name_col]
        source_feature_cols = [c for c in feature_cols if c in source_df.columns]

        X_target = target_df[source_feature_cols].fillna(0).values
        X_source = source_df[source_feature_cols].fillna(0).values
        y_source = source_df[label_column].values

        # 4. Preprocess
        X_source_scaled, X_target_scaled = self.preprocessor.normalize(X_source, X_target)

        # 5. Apply CORAL domain adaptation
        if coral_option:
            X_target_adapted = self.coral.align(X_source_scaled, X_target_scaled)
        else:
            X_target_adapted = X_target_scaled

        # 6. KNN prediction
        predictions = self.knn.predict(X_source_scaled, y_source, X_target_adapted, k=knn_value)

        # 7. Build result
        result = []
        for i, (_, row) in enumerate(target_df.iterrows()):
            result.append({
                "class": row.get(name_col, str(i)),
                "prediction": str(predictions[i])
            })

        return {"status": "success", "predictions": result}
