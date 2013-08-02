/**
 * 
 */
package resonantinduction.fx;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import org.lwjgl.opengl.GL11;

import resonantinduction.ResonantInduction;
import resonantinduction.base.Vector3;
import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Electric shock Fxs.
 * 
 * @author Calclavia
 * 
 */
@SideOnly(Side.CLIENT)
public class FXElectricBolt extends EntityFX
{
	public static final ResourceLocation FADED_SPHERE = new ResourceLocation(ResonantInduction.DOMAIN, ResonantInduction.MODEL_TEXTURE_DIRECTORY + "fadedSphere.png");
	public static final ResourceLocation PARTICLE_RESOURCE = new ResourceLocation("textures/particle/particles.png");

	private final float boltWidth = 0.05f;
	private final float complexity = 2;
	private BoltPoint start;
	private BoltPoint target;
	private double boltLength;
	private int segmentCount;

	private Random random;

	private Set<BoltSegment> segments = new HashSet<BoltSegment>();
	private int maxSplitID;
	private final Map<Integer, Integer> parentIDMap = new HashMap<Integer, Integer>();

	public FXElectricBolt(World world, Vector3 start, Vector3 target)
	{
		super(world, start.x, start.y, start.z);
		this.start = new BoltPoint(target);
		this.target = new BoltPoint(target);
		this.particleAge = (3 + this.rand.nextInt(3) - 1);
		this.random = new Random();
		this.boltLength = start.distance(target);
		this.setUp();
	}

	public FXElectricBolt setColor(float r, float g, float b)
	{
		this.particleRed = r;
		this.particleGreen = g;
		this.particleBlue = b;
		return this;
	}

	public void setUp()
	{
		this.segments.add(new BoltSegment(this.start, this.target));
		double offsetRatio = this.boltLength * this.complexity;
		this.split(offsetRatio / 8, 0.1f, 45);

	}

	public void split(double offset, float length, float angle)
	{
		int splitAmount = 2;
		Set<BoltSegment> oldSegments = this.segments;
		this.segments.clear();

		BoltSegment prev = null;

		for (BoltSegment segment : oldSegments)
		{
			Vector3 subSegment = segment.getDifference().scale(1 / splitAmount);

			BoltPoint[] newPoints = new BoltPoint[splitAmount + 1];
			newPoints[0] = segment.start;
			newPoints[splitAmount + 1] = segment.end;

			for (int i = 1; i < splitAmount; i++)
			{
				Vector3 newOffset = segment.getDifference().getPerpendicular().rotate(random.nextFloat() * 360, segment.getDifference()).scale((this.random.nextFloat() / 2) * offset);
				Vector3 basePoint = newPoints[0].clone().translate(subSegment.clone().scale(i));
				newPoints[i] = new BoltPoint(basePoint, newOffset);
			}

			for (int i = 1; i < splitAmount; i++)
			{
				BoltSegment next = new BoltSegment(newPoints[i], newPoints[(i + 1)], segment.weight, segment.id * splitAmount + i, segment.splitID);
				next.prev = prev;

				if (prev != null)
				{
					prev.next = next;
				}

				if (i != 0)
				{
					Vector3 splitrot = next.difference.xCrossProduct().rotate(this.rand.nextFloat() * 360.0F, next.difference);
					Vector3 diff = next.difference.clone().rotate((this.rand.nextFloat() * 0.66F + 0.33F) * angle, splitrot).scale(length);
					this.parentIDMap.put(this.maxSplitID, next.splitID);
					BoltSegment split = new BoltSegment(newPoints[i], new BoltPoint(newPoints[(i + 1)].base, newPoints[(i + 1)].offset.clone().translate(diff)), segment.weight / 2.0F, next.id, this.maxSplitID);
					split.prev = prev;
					this.maxSplitID++;
					this.segments.add(split);
				}

				prev = next;
				this.segments.add(next);
			}

			if (segment.next != null)
			{
				segment.next = prev;
			}
		}
	}

	@Override
	public void onUpdate()
	{
		this.prevPosX = this.posX;
		this.prevPosY = this.posY;
		this.prevPosZ = this.posZ;

		if (this.particleAge++ >= this.particleMaxAge)
		{
			this.setDead();
		}
	}

	@Override
	public void renderParticle(Tessellator tessellator, float partialFrame, float cosYaw, float cosPitch, float sinYaw, float sinSinPitch, float cosSinPitch)
	{
		EntityPlayer player = Minecraft.getMinecraft().thePlayer;

		tessellator.draw();
		FMLClientHandler.instance().getClient().renderEngine.func_110577_a(FADED_SPHERE);
		GL11.glPushMatrix();

		/**
		 * Do rendering here.
		 */
		GL11.glDepthMask(true);
		GL11.glEnable(3042);

		Vector3 playerVector = new Vector3(sinYaw * -cosPitch, -cosSinPitch / cosYaw, cosYaw * cosPitch);
		int renderLength = (int) ((this.particleAge + partialFrame + this.boltLength * 3) / (this.boltLength * 3 * this.segmentCount));

		for (BoltSegment segment : this.segments)
		{
			// TODO: Weight? Scale
			double width = this.width * (new Vector3(player).distance(segment.start) / 5 + 1);
			Vector3 prevDiff = playerVector.crossProduct(segment.prevDiff).scale(this.width);
			Vector3 nextDiff = playerVector.crossProduct(segment.nextDiff).scale(this.width);

			Vector3 renderStart = segment.start;
			Vector3 renderEnd = segment.end;
			tessellator.setColorRGBA_F(this.particleRed, this.particleGreen, this.particleBlue, this.particleAlpha);

			float rx1 = (float) (renderStart.x - interpPosX);
			float ry1 = (float) (renderStart.y - interpPosY);
			float rz1 = (float) (renderStart.z - interpPosZ);
			float rx2 = (float) (renderEnd.x - interpPosX);
			float ry2 = (float) (renderEnd.y - interpPosY);
			float rz2 = (float) (renderEnd.z - interpPosZ);

			tessellator.addVertexWithUV(rx2 - nextDiff.x, ry2 - nextDiff.y, rz2 - nextDiff.z, 0.5D, 0.0D);
			tessellator.addVertexWithUV(rx1 - prevDiff.x, ry1 - prevDiff.y, rz1 - prevDiff.z, 0.5D, 0.0D);
			tessellator.addVertexWithUV(rx1 + prevDiff.x, ry1 + prevDiff.y, rz1 + prevDiff.z, 0.5D, 1.0D);
			tessellator.addVertexWithUV(rx2 + nextDiff.x, ry2 + nextDiff.y, rz2 + nextDiff.z, 0.5D, 1.0D);
		}

		GL11.glDisable(3042);
		GL11.glDepthMask(false);

		GL11.glPopMatrix();
		FMLClientHandler.instance().getClient().renderEngine.func_110577_a(PARTICLE_RESOURCE);
		tessellator.startDrawingQuads();
	}

	@Override
	public boolean shouldRenderInPass(int pass)
	{
		return pass == 2;
	}

	public class BoltPoint extends Vector3
	{
		public Vector3 base;
		public Vector3 offset;

		public BoltPoint(Vector3 base, Vector3 offset)
		{
			super(base.translate(offset));
			this.base = base;
			this.offset = offset;
		}

		public BoltPoint(Vector3 base)
		{
			this(base, new Vector3());
		}
	}

	public class BoltSegment
	{
		public BoltPoint start;
		public BoltPoint end;
		public BoltSegment prev;
		public BoltSegment next;
		public Vector3 prevDiff;
		public Vector3 nextDiff;
		public float weight;
		public int id;
		public int splitID;

		private Vector3 difference;

		public BoltSegment(BoltPoint start, BoltPoint end)
		{
			this.start = start;
			this.end = end;
			this.difference = this.end.difference(this.start);
		}

		public BoltSegment(BoltPoint start, BoltPoint end, float weight, int id, int splitID)
		{
			this(start, end);
			this.weight = weight;
			this.id = id;
			this.splitID = splitID;
		}

		public Vector3 getDifference()
		{
			return difference;
		}
	}
}