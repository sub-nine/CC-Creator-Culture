package com.sub9.productservice.support;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

public final class ImageTestFixture {
  private ImageTestFixture() {}

  public static byte[] imageBytes(String format) throws IOException {
    var output = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), format, output);
    return output.toByteArray();
  }
}
