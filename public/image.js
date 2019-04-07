/*jshint esversion: 6 */
/*jshint bitwise: false*/

// Global objects
const sourceCanvas = document.getElementById('sourceCanvas');
const scriptCanvas = document.getElementById('scriptCanvas');
const script = []; // finished when long enough

const prepImage = (img) => {

  sourceCanvas.width = img.width;
  sourceCanvas.height = img.height;
  scriptCanvas.width = sourceCanvas.width;
  scriptCanvas.height = sourceCanvas.height;
  sourceCanvas.getContext('2d').drawImage(img, 0, 0);

  while (script.length > 0) {
    script.pop();
  }
  script.push([Math.floor(sourceCanvas.width / 2), Math.floor(sourceCanvas.height / 2)]);

  window.requestAnimationFrame(imageToLineDrawing);
};

// Start with default
const img = new Image();
img.onload = () => {
  console.info('Starting on default image.');
  prepImage(img);
};
img.src = 'liberty.png';

// Custom file upload (optional)
const handleImageUpload = e => {
  const reader = new FileReader();
  reader.onload = event => {
    const img = new Image();
    img.onload = () => {
      console.info('Starting on custom uploaded image.');
      prepImage(img);
    };
    img.src = event.target.result;
  };
  reader.readAsDataURL(e.target.files[0]);
};
const imageLoader = document.getElementById('imageLoader');
imageLoader.addEventListener('change', handleImageUpload, false);


// https://stackoverflow.com/questions/4672279/bresenham-algorithm-in-javascript
const lineToPoints = (p1, p2) => {
  let [x1, y1] = p1;
  const [x2, y2] = p2;

  const coordinatesArray = [],
    dx = Math.abs(x2 - x1),
    dy = Math.abs(y2 - y1),
    sx = (x1 < x2) ? 1 : -1,
    sy = (y1 < y2) ? 1 : -1;

  let err = dx - dy;
  // Set first coordinates
  coordinatesArray.push([x1, y1]);
  // Main loop
  while (!((x1 === x2) && (y1 === y2))) {
    const e2 = err << 1;
    if (e2 > -dy) {
      err -= dy;
      x1 += sx;
    }
    if (e2 < dx) {
      err += dx;
      y1 += sy;
    }
    coordinatesArray.push([x1, y1]);
  }
  return coordinatesArray;
};

/** Draw just one more step */
const imageToLineDrawing = () => {
  const width = sourceCanvas.width,
    height = sourceCanvas.height,
    sourceCtx = sourceCanvas.getContext('2d'),
    scriptCtx = scriptCanvas.getContext('2d');

  // Faster to get .data now once
  const sourceImageData = sourceCtx.getImageData(0, 0, width, height).data;

  // Convert to grayscale (could mess with contrast here)
  /*
  const data = sourceImageData.data;
  for (let i = 0; i < data.length; i += 4) {
    const avg = (data[i] + data[i + 1] + data[i + 2]) / 3;
    data[i] = avg; // red
    data[i + 1] = avg; // green
    data[i + 2] = avg; // blue
  }
  sourceCtx.putImageData(sourceImageData, 0, 0);
  console.info("Converted to grayscale.");
  */

  // https://developer.mozilla.org/en-US/docs/Web/API/Canvas_API/Tutorial/Pixel_manipulation_with_canvas
  const pixelToLum = (p1) => {
    const redIndex = p1[1] * (width * 4) + p1[0] * 4;
    return sourceImageData[redIndex];
  };

  const nextStepScript = new Path2D();

  let point = script.slice(-1)[0];
  nextStepScript.moveTo(point[0], point[1]);

  let leastLum = 255;
  let leastLumPoint = [];
  for (let sampleNum = 0; sampleNum < 5000; sampleNum++) {
    const nextPoint = [
      Math.floor(Math.random() * width),
      Math.floor(Math.random() * height)
    ];
    const pixelsOnLine = lineToPoints(point, nextPoint);
    const avgLum = pixelsOnLine.reduce((totalLum, p1) => totalLum + pixelToLum(p1), 0) / pixelsOnLine.length;
    if (avgLum < leastLum) {
      leastLum = avgLum;
      leastLumPoint = nextPoint;
      // console.debug(`Sample ${sampleNum} found a new best: (${leastLumPoint})=${leastLum}`);
    }
  }
  nextStepScript.lineTo(leastLumPoint[0], leastLumPoint[1]);
  script.push(leastLumPoint);

  sourceCtx.strokeStyle = '#FFFFFF';
  sourceCtx.lineWidth = 2.0;
  sourceCtx.stroke(nextStepScript);
  scriptCtx.stroke(nextStepScript);

  if (script.length < 500) {
    window.requestAnimationFrame(imageToLineDrawing);
  }
};
