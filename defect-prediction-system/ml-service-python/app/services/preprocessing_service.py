from sklearn.preprocessing import StandardScaler
import numpy as np


class PreprocessingService:
    def normalize(self, X_source: np.ndarray, X_target: np.ndarray):
        scaler = StandardScaler()
        X_source_scaled = scaler.fit_transform(X_source)
        X_target_scaled = scaler.transform(X_target)
        return X_source_scaled, X_target_scaled
