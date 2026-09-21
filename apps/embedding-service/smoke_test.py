"""python -m unittest discover -s apps/embedding-service -p '*_test.py'"""

import io
import json
import unittest
from unittest.mock import patch

from smoke import check


class Response(io.BytesIO):
    status = 200


class SmokeTest(unittest.TestCase):
    def test_valid_vector_requires_exactly_one_inference(self):
        replies = [Response(b"docs"), Response(json.dumps({"vector": [0.1] * 768}).encode())]
        with patch("urllib.request.urlopen", side_effect=replies) as request:
            check("http://localhost:8000")
        self.assertEqual(request.call_count, 2)
        self.assertEqual(request.call_args.args[0].method, "POST")
        self.assertEqual(request.call_args.args[0].full_url, "http://localhost:8000/embed")

    def test_invalid_vectors_are_rejected(self):
        for vector in (None, [0] * 767, [True] * 768, [float("nan")] * 768, ["0"] * 768):
            with self.subTest(vector_type=type(vector)):
                replies = [Response(b"docs"), Response(json.dumps({"vector": vector}).encode())]
                with patch("urllib.request.urlopen", side_effect=replies):
                    with self.assertRaises(ValueError):
                        check("http://localhost:8000")


if __name__ == "__main__":
    unittest.main()
