package org.firstinspires.ftc.teamcode.drive.opmode;

import static com.qualcomm.robotcore.hardware.DcMotor.RunMode.RUN_TO_POSITION;
import static com.qualcomm.robotcore.hardware.DcMotor.RunMode.RUN_USING_ENCODER;

import com.acmerobotics.roadrunner.geometry.Pose2d;
import com.acmerobotics.roadrunner.geometry.Vector2d;
import com.acmerobotics.roadrunner.trajectory.Trajectory;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.util.Range;
import com.acmerobotics.roadrunner.drive.DriveSignal;
import com.acmerobotics.roadrunner.drive.MecanumDrive;
import com.acmerobotics.roadrunner.followers.HolonomicPIDVAFollower;
import com.acmerobotics.roadrunner.followers.TrajectoryFollower;
import com.acmerobotics.roadrunner.geometry.Pose2d;
import com.acmerobotics.roadrunner.trajectory.Trajectory;
import com.acmerobotics.roadrunner.trajectory.TrajectoryBuilder;
import com.acmerobotics.roadrunner.trajectory.constraints.AngularVelocityConstraint;
import com.acmerobotics.roadrunner.trajectory.constraints.MecanumVelocityConstraint;
import com.acmerobotics.roadrunner.trajectory.constraints.MinVelocityConstraint;
import com.acmerobotics.roadrunner.trajectory.constraints.ProfileAccelerationConstraint;
import com.acmerobotics.roadrunner.trajectory.constraints.TrajectoryAccelerationConstraint;
import com.acmerobotics.roadrunner.trajectory.constraints.TrajectoryVelocityConstraint;
import org.firstinspires.ftc.robotcore.external.hardware.camera.BuiltinCameraDirection;
import org.firstinspires.ftc.robotcore.external.hardware.camera.WebcamName;
import org.firstinspires.ftc.robotcore.external.hardware.camera.controls.ExposureControl;
import org.firstinspires.ftc.robotcore.external.hardware.camera.controls.GainControl;
import org.firstinspires.ftc.robotcore.external.tfod.Recognition;
import org.firstinspires.ftc.teamcode.drive.SampleMecanumDrive;
import org.firstinspires.ftc.teamcode.trajectorysequence.TrajectorySequence;
import org.firstinspires.ftc.teamcode.trajectorysequence.sequencesegment.TrajectorySegment;
import org.firstinspires.ftc.vision.VisionPortal;
import org.firstinspires.ftc.vision.apriltag.AprilTagDetection;
import org.firstinspires.ftc.vision.apriltag.AprilTagProcessor;
import org.firstinspires.ftc.vision.tfod.TfodProcessor;

import java.util.List;
import java.util.concurrent.TimeUnit;

/*
 * This is an example of a more complex path to really test the tuning.
 */
@Autonomous(name = "testing", group = "drive")
public class testingRR extends LinearOpMode {
    private static final boolean USE_WEBCAM = true;  // true for webcam, false for phone camera

    private static final String TFOD_MODEL_ASSET = "RedProp.tflite";

    public static double VX_WEIGHT = 1;
    public static double VY_WEIGHT = 1;
    public static double OMEGA_WEIGHT = 1;
    String position = "";

    final double DESIRED_DISTANCE = 8.5; //  this is how close the camera should get to the target (inches)

    //  Set the GAIN constants to control the relationship between the measured position error, and how much power is
    //  applied to the drive motors to correct the error.
    //  Drive = Error * Gain    Make these values smaller for smoother control, or larger for a more aggressive response.
    final double SPEED_GAIN  =  0.02;   //  Forward Speed Control "Gain". eg: Ramp up to 50% power at a 25 inch error.   (0.50 / 25.0)
    final double STRAFE_GAIN =  0.02;   //  Strafe Speed Control "Gain".  eg: Ramp up to 25% power at a 25 degree Yaw error.   (0.25 / 25.0)
    final double TURN_GAIN   =  0.02;   //  Turn Control "Gain".  eg: Ramp up to 25% power at a 25 degree error. (0.25 / 25.0)

    final double MAX_AUTO_SPEED = 0.7;   //  Clip the approach speed to this max value (adjust for your robot)
    final double MAX_AUTO_STRAFE= 0.7;   //  Clip the approach speed to this max value (adjust for your robot)
    final double MAX_AUTO_TURN  = 0.7;

    private  int DESIRED_TAG_ID = 5;
    private int ticksForCascade = 920;
    private double DESIRED_STRAFE = 0;
    private AprilTagProcessor aprilTag;
    private AprilTagDetection desiredTag = null;

    boolean targetFound     = false;    // Set to true when an AprilTag target is detected
    double  drive           = 0;        // Desired forward power/speed (-1 to +1)
    double  strafe          = 0;        // Desired strafe power/speed (-1 to +1)
    double  turn            = 0;        // Desired turning power/speed (-1 to +1)

    String park = "";


    boolean indicator = false;
    private static final String[] LABELS = {
            "RedProp"

    };
    /**
     * The variable to store our instance of the TensorFlow Object Detection processor.
     */
    private TfodProcessor tfod;

    /**
     * The variable to store our instance of the vision portal.
     */
    private VisionPortal visionPortal;

    @Override
    public void runOpMode() throws InterruptedException {
        SampleMecanumDrive robot = new SampleMecanumDrive(hardwareMap);
        initTfod();
        robot.resetEncodersCascade();
//        robot.launch.setPosition(0.65);
        robot.arm.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
        robot.arm.setTargetPosition(0);
        robot.arm.setMode(DcMotor.RunMode.RUN_TO_POSITION);
        robot.arm.setPower(.9);
        robot.wrist.setPosition(0.675);
        robot.claw.setPosition(0);
        waitForStart();

        if (isStopRequested()) return;
        Pose2d startingPose = new Pose2d(14, -60, Math.toRadians(90));
        robot.setPoseEstimate(startingPose);
        TrajectorySequence traj = robot.trajectorySequenceBuilder(startingPose)
                .forward(30.5)
                .lineTo(new Vector2d(14,-35))
                .turn(Math.toRadians(-90))
//                .addDisplacementMarker(() ->{
////                    robot.timer.reset();
//////        robot.cascadeLock(ticksForCascade);
////                    robot.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
////                    setManualExposure(6, 250);
////                    while (robot.timer.seconds() < 1.75){
////                        aprilTagDetection(robot);
////                        Pose2d poseEstimate = robot.getPoseEstimate();
////                        telemetry.addData("x", poseEstimate.getX());
////                        telemetry.addData("y", poseEstimate.getY());
////                        telemetry.addData("heading", poseEstimate.getHeading());
////                        telemetry.update();
//                    }

//                })
                .build();


        // april tag






//        Trajectory traj6 = robot.trajectoryBuilder(traj5.end())
//                .lineTo(new Vector2d(40, -23.3))
//                .build();
//        Trajectory traj7 = robot.trajectoryBuilder(traj6.end())
//                .splineToConstantHeading(new Vector2d(58.5, -11), Math.toRadians(0))
//                .build();



        robot.followTrajectorySequence(traj);
//        .addDisplacementMarker(() -> {
            robot.timer.reset();
//        robot.cascadeLock(ticksForCascade);
            robot.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
            setManualExposure(6, 250);
            while (robot.timer.seconds() < 2){
                aprilTagDetection(robot);
                Pose2d poseEstimate = robot.getPoseEstimate();
                telemetry.addData("x", poseEstimate.getX());
                telemetry.addData("y", poseEstimate.getY());
                telemetry.addData("heading", poseEstimate.getHeading());
                telemetry.update();
            }


//                    robot.setMode(DcMotor.RunMode.);
//        })
//        robot.dropper.setPosition(1);
//        robot.followTrajectory(traj1);
//        robot.turn(Math.toRadians(-90));
        Pose2d poseEstimate = robot.getPoseEstimate();
        Trajectory errorApril = robot.trajectoryBuilder(new Pose2d(poseEstimate.getX(),poseEstimate.getY(),poseEstimate.getHeading())) //26.67, -33.3
                .forward(desiredTag.ftcPose.range - DESIRED_DISTANCE)
                .build();
        robot.followTrajectory(errorApril);
        TrajectorySequence traj1 = robot.trajectorySequenceBuilder(errorApril.end())
                .lineTo(new Vector2d(35,-35))
//                        .addDisplacementMarker(0.5, 0, () -> {
//                    robot.claw.setPosition(0);
//                    robot.wrist.setPosition(1);
//                })
                .lineToLinearHeading(new Pose2d(23.1, -7,Math.toRadians(0)))
                .lineToLinearHeading(new Pose2d(-53, -7,Math.toRadians(0)))
                .addDisplacementMarker(() -> {
                    robot.wrist.setPosition(.35);
                    robot.claw.setPosition(0.75);
                    robot.arm.setTargetPosition(-75);
                })

                .setConstraints(SampleMecanumDrive.getVelocityConstraint(12, Math.toRadians(48), 19.075), SampleMecanumDrive.getAccelerationConstraint(10))

                .lineToLinearHeading(new Pose2d(-61, -6.95,Math.toRadians(0)))

//                .addDisplacementMarker(() -> {
//                    robot.wrist.setPosition(.4);
//                    robot.arm.setTargetPosition(-85);
//                    robot.claw.setPosition(0);
//                })
                .waitSeconds(1)
                .resetConstraints()
//                .lineTo(new Vector2d(-55, -7))
                .build();

        robot.followTrajectorySequence(traj1);
        robot.claw.setPosition(0);
        sleep(1000);
        TrajectorySequence traj2 = robot.trajectorySequenceBuilder(traj1.end())
                .lineTo(new Vector2d(23.1, -7))
                .lineTo(new Vector2d(48, -29))
//                                .splineToConstantHeading(new Vector2d(-60, -11.5), Math.toRadians(0))
                .lineTo(new Vector2d(31.5, -7))
                .lineTo(new Vector2d(59, -7))
                        .build();
        robot.followTrajectorySequence(traj2);



        //        robot.arm.setTargetPosition(450);
//        sleep(250);
//        robot.wrist.setPosition(0.58);
//        sleep(100);
//        robot.claw.setPosition(.47);
//        sleep(25);
//        robot.claw.setPosition(0.8);

        //put another thing
//        robot.followTrajectory(back1);
//        robot.claw.setPosition(0);
//        robot.wrist.setPosition(1);


//        robot.arm.setTargetPosition(0);
//        sleep(100);
//        while (robot.arm.getCurrentPosition() > 50)
//            sleep(50);
//        robot.cascadeLock(0);


//        robot.followTrajectory(traj2);
//
//        robot.followTrajectory(traj3);
//
//        robot.followTrajectory(traj4);
//
//        robot.followTrajectory(traj5);
//
//        robot.followTrajectory(traj6);
//
//        robot.followTrajectory(traj7);

        // Share the CPU.
        sleep(20);

        // Save more CPU resources when camera is no longer needed.
        visionPortal.close();
    }
    private void initTfod() {

        // Create the TensorFlow processor by using a builder.
        tfod = new TfodProcessor.Builder()

                // Use setModelAssetName() if the TF Model is built in as an asset.
                // Use setModelFileName() if you have downloaded a custom team model to the Robot Controller.
                .setModelAssetName(TFOD_MODEL_ASSET)
                //.setModelFileName(TFOD_MODEL_FILE)

                .setModelLabels(LABELS)
//            .setIsModelTensorFlow2(true)
//            .setIsModelQuantized(true)
//            .setModelInputSize(300)
//            .setModelAspectRatio(16.0 / 9.0)

                .build();

        aprilTag = new AprilTagProcessor.Builder().build();

        // Create the vision portal by using a builder.
        VisionPortal.Builder builder = new VisionPortal.Builder();

        // Set the camera (webcam vs. built-in RC phone camera).
        if (USE_WEBCAM) {
            builder.setCamera(hardwareMap.get(WebcamName.class, "Webcam 1"));
        } else {
            builder.setCamera(BuiltinCameraDirection.BACK);
        }

        // Choose a camera resolution. Not all cameras support all resolutions.
        //builder.setCameraResolution(new Size(640, 480));

        // Enable the RC preview (LiveView).  Set "false" to omit camera monitoring.
        //builder.enableCameraMonitoring(true);

        // Set the stream format; MJPEG uses less bandwidth than default YUY2.
        //builder.setStreamFormat(VisionPortal.StreamFormat.YUY2);

        // Choose whether or not LiveView stops if no processors are enabled.
        // If set "true", monitor shows solid orange screen if no processors enabled.
        // If set "false", monitor shows camera view without annotations.
        //builder.setAutoStopLiveView(false);

        // Set and enable the processor.
        builder.addProcessor(tfod);
        builder.addProcessor(aprilTag);

        // Build the Vision Portal, using the above settings.
        visionPortal = builder.build();

        // Set confidence threshold for TFOD recognitions, at any time.
        //tfod.setMinResultConfidence(0.75f);

        // Disable or re-enable the TFOD processor at any time.
        //visionPortal.setProcessorEnabled(tfod, true);

    }   // end method initTfod()

    /**
     * Add telemetry about TensorFlow Object Detection (TFOD) recognitions.
     */
    private void telemetryTfod() {

        List<Recognition> currentRecognitions = tfod.getRecognitions();


        // Step through the list of recognitions and display info for each one.
        for (Recognition recognition : currentRecognitions) {
            double x = (recognition.getLeft() + recognition.getRight()) / 2 ;
            double y = (recognition.getTop()  + recognition.getBottom()) / 2 ;
            telemetry.addData(""," ");
            telemetry.addData("Image", "%s (%.0f %% Conf.)", recognition.getLabel(), recognition.getConfidence() * 100);
            telemetry.addData("- Position", "%.0f / %.0f", x, y);
            telemetry.addData("- Size", "%.0f x %.0f", recognition.getWidth(), recognition.getHeight());
            telemetry.update();
            if(x > 400){
                position = "Right";
                break;
            }
            else if(x > 50 && x < 300){
                position = "Center";
                break;
            }
            else{
                position = "Left";
                break;
            }

        }   // end for() loop

    }
    public void moveRobot(double x, double y, double yaw, SampleMecanumDrive robot) {
        // Calculate wheel powers.
        double leftFrontPower    =  x -y -yaw;
        double rightFrontPower   =  x +y +yaw;
        double leftBackPower     =  x +y -yaw;
        double rightBackPower    =  x -y +yaw;

        // Normalize wheel powers to be less than 1.0
        double max = Math.max(Math.abs(leftFrontPower), Math.abs(rightFrontPower));
        max = Math.max(max, Math.abs(leftBackPower));
        max = Math.max(max, Math.abs(rightBackPower));

        if (max > 1.0) {
            leftFrontPower /= max;
            rightFrontPower /= max;
            leftBackPower /= max;
            rightBackPower /= max;
        }

        // Send powers to the wheels.
        robot.frontLeft.setPower(rightBackPower);
        robot.frontRight.setPower(leftBackPower);
        robot.backLeft.setPower(rightFrontPower);
        robot.backRight.setPower(leftFrontPower);

        /*if (desiredTag.ftcPose.range > DESIRED_DISTANCE) {
            while (desiredTag.ftcPose.range > DESIRED_DISTANCE) {
                robot.setPowerOfAllMotorsTo(-.3);
            }
            robot.setPowerOfAllMotorsTo(0);
        } else if (desiredTag.ftcPose.range < DESIRED_DISTANCE) {
            while (desiredTag.ftcPose.range < DESIRED_DISTANCE) {
                robot.setPowerOfAllMotorsTo(.3);
            }
            robot.setPowerOfAllMotorsTo(0);
        }*/
    }

    /**
     * Initialize the AprilTag processor.
     */
    private void initAprilTag() {
        // Create the AprilTag processor by using a builder.
        aprilTag = new AprilTagProcessor.Builder().build();

        // Create the vision portal by using a builder.
        if (USE_WEBCAM) {
            visionPortal = new VisionPortal.Builder()
                    .setCamera(hardwareMap.get(WebcamName.class, "Webcam 1"))
                    .addProcessor(aprilTag)
                    .build();

        } else {
            visionPortal = new VisionPortal.Builder()
                    .setCamera(BuiltinCameraDirection.BACK)
                    .addProcessor(aprilTag)
                    .build();
        }
    }

    /*
     Manually set the camera gain and exposure.
     This can only be called AFTER calling initAprilTag(), and only works for Webcams;
    */
    private void setManualExposure(int exposureMS, int gain) {
        // Wait for the camera to be open, then use the controls

        if (visionPortal == null) {
            return;
        }

        // Make sure camera is streaming before we try to set the exposure controls
        if (visionPortal.getCameraState() != VisionPortal.CameraState.STREAMING) {
            telemetry.addData("Camera", "Waiting");
            telemetry.update();
            while (!isStopRequested() && (visionPortal.getCameraState() != VisionPortal.CameraState.STREAMING)) {
                sleep(20);
            }
            telemetry.addData("Camera", "Ready");
            telemetry.update();
        }

        // Set camera controls unless we are stopping.
        if (!isStopRequested())
        {
            ExposureControl exposureControl = visionPortal.getCameraControl(ExposureControl.class);
            if (exposureControl.getMode() != ExposureControl.Mode.Manual) {
                exposureControl.setMode(ExposureControl.Mode.Manual);
                sleep(50);
            }
            exposureControl.setExposure((long)exposureMS, TimeUnit.MILLISECONDS);
            sleep(20);
            GainControl gainControl = visionPortal.getCameraControl(GainControl.class);
            gainControl.setGain(gain);
            sleep(20);
        }
    }
    public void  aprilTagDetection(SampleMecanumDrive robot){

        targetFound = false;
        desiredTag = null;

        // Step through the list of detected tags and look for a matching tag
        List<AprilTagDetection> currentDetections = aprilTag.getDetections();
        for (AprilTagDetection detection : currentDetections) {
            if ((detection.metadata != null) && (detection.id == DESIRED_TAG_ID)) {
                targetFound = true;
                desiredTag = detection;

                break;  // don't look any further.
            }
        }
        // Tell the driver what we see, and what to do.
        // If Left Bumper is being pressed, AND we have found the desired target, Drive to target Automatically .
        if (targetFound) {

            // Determine heading, range and Yaw (tag image rotation) error so we can use them to control the robot automatically.
            double rangeError = (desiredTag.ftcPose.range - DESIRED_DISTANCE);
            double headingError = desiredTag.ftcPose.bearing;
            double yawError = desiredTag.ftcPose.yaw;

            // Use the speed and turn "gains" to calculate how we want the robot to move.
            drive = Range.clip(rangeError * SPEED_GAIN, -MAX_AUTO_SPEED, MAX_AUTO_SPEED);
            turn = Range.clip(headingError * TURN_GAIN, -MAX_AUTO_TURN, MAX_AUTO_TURN);
            strafe = Range.clip(-yawError * STRAFE_GAIN, -MAX_AUTO_STRAFE, MAX_AUTO_STRAFE);

            telemetry.addData("Auto", "Drive %5.2f, Strafe %5.2f, Turn %5.2f ", drive, strafe, turn);
        }
        else{
            // Use the speed and turn "gains" to calculate how we want the robot to move.
            drive = 0;
            turn = 0;
            strafe = 0;
            telemetry.addLine("Target not found");
        }
        telemetry.update();
        robot.update();
        moveRobot(drive, strafe, turn, robot);
//        robot.setWeightedDrivePower(new Pose2d(drive, strafe, turn));
        sleep(10);

        // Apply desired axes motions to the drivetrain
    }

}
