import numpy as np
from sklearn.neighbors import KNeighborsClassifier


class KnnService:
    """KNN-based defect classifier."""

    def predict(self, X_source: np.ndarray, y_source: np.ndarray,
                X_target: np.ndarray, k: int = 5) -> np.ndarray:
        classifier = KNeighborsClassifier(n_neighbors=k, metric='euclidean', weights='distance')
        classifier.fit(X_source, y_source)
        return classifier.predict(X_target)

    def fit(self, X_source: np.ndarray, y_source: np.ndarray, k: int = 5) -> KNeighborsClassifier:
        classifier = KNeighborsClassifier(n_neighbors=k, metric='euclidean', weights='distance')
        classifier.fit(X_source, y_source)
        return classifier
