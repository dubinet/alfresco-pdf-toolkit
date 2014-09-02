package org.alfresco.extension.pdftoolkit.transformer;

import org.alfresco.service.cmr.repository.TransformationOptions;
import org.springframework.extensions.surf.util.ParameterCheck;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by peter on 2014-01-14.
 */
public class ITextTransformerPDFToPDFOptions extends TransformationOptions
{
    private static final String OPT_COMPRESSION = "compression";
    private static final String OPT_IMAGE_COMPRESSION = "image-compression";

    /** The default compression level */
    private String compression = "9";
    /** The default image compression level */
    private String image_compression = "5";

    public void setCompression(String compression) {
        ParameterCheck.mandatory(OPT_COMPRESSION, compression);
        this.compression = compression;
    }

    public void setImage_compression(String image_compression) {
        ParameterCheck.mandatory(OPT_IMAGE_COMPRESSION, image_compression);
        this.image_compression = image_compression;
    }

    public String getCompression() {
        return compression;
    }

    public String getImage_compression() {
        return image_compression;
    }

    @Override
    public Map<String, Object> toMap()
    {
        Map<String, Object> baseProps = super.toMap();
        Map<String, Object> props = new HashMap<String, Object>(baseProps);
        props.put(OPT_COMPRESSION, compression);
        props.put(OPT_IMAGE_COMPRESSION, image_compression);
        return props;
    }
}
