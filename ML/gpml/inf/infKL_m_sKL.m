function [post nlZ dnlZ] = infKL_m_sKL(hyp, mean, cov, lik, x, y)
lik_name = func2str(lik{1});

if hyp.is_cached==1
	global cache_post;
	global cache_nlz;
	global cache_idx;
	
	post=cache_post(cache_idx);
	nlZ=cache_nlz(cache_idx);
	if nargout>2
		warning('to be implemented\n');
		dnlZ = NaN;
	end
	return 
end

snu2=hyp.snu2;
x_batch=x;
y_batch=y;
n_batch=size(x_batch,1);
assert(size(y,1)==n_batch)
C=max(y);

% GP prior
K_batch = feval(cov{:}, hyp.cov, x_batch);                  % evaluate the covariance matrix
m_batch = feval(mean{:}, hyp.mean, x_batch);                      % evaluate the mean vector
K_batch=snu2*eye(n_batch)+K_batch;
K_C_batch=zeros(C*n_batch,C*n_batch);
for j=1:C
	K_C_batch((j-1)*n_batch+1:j*n_batch,(j-1)*n_batch+1:j*n_batch)=K_batch;
end


%the size of mini batch= n_batch * mini_batch_rate
%mini_batch_rate=0.001;
%mini_batch_size=floor(n_batch*mini_batch_rate);
mini_batch_size=hyp.mini_batch_size;
assert (mini_batch_size>0)
mini_batch_num=ceil(n_batch/mini_batch_size);
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
%init value
post_m_batch=hyp.init_m;%k=0
V_u_batch=hyp.init_V;
tW_batch = zeros(n_batch,1);%k=-1
post_v_batch=diag(K_batch);%k=0

m_u_batch=hyp.init_m;%k=0
V_u_batch=hyp.init_V;
%{
alpha_u_batch=zeros(C*n_batch,1);
for j=1:C
  idx=(j-1)*n_batch+1:j*n_batch;
  alpha_u_batch(idx,:)=K_batch\(m_u_batch(idx,:)-m);
end
%}
assert(size(m_u_batch,1)==C*n_batch);
assert(size(V_u_batch,1)==C*n_batch);
assert(size(V_u_batch,2)==C*n_batch);
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

momentum_m_u_batch=zeros(n_batch*C,1);
momentum_V_u_batch=zeros(n_batch*C,n_batch*C);

epsilon=1e-8;
g_acc_m_u_batch=zeros(n_batch*C,1);
g_delta_acc_m_u_batch=zeros(n_batch*C,1);
g_acc_V_u_batch=zeros(n_batch*C,n_batch*C);
g_delta_acc_V_u_batch=zeros(n_batch*C,n_batch*C);


iter = 0;
pass=0;
max_pass=hyp.max_pass;
while pass<max_pass
	index=randperm(n_batch);
	offset=0;
	mini_batch_counter=0;
	debug_count=0;
	while mini_batch_counter<mini_batch_num
		iter=iter+1;
		to_idx=(mini_batch_counter+1)*mini_batch_size;
		if to_idx>n_batch
			to_idx=n_batch;
		end
		from_idx=mini_batch_counter*mini_batch_size+1;

		idx=index(from_idx:to_idx);
		x=x_batch(idx,:);
		y=y_batch(idx);

		%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
		%mini batch
		rate=hyp.learning_rate;
		weight=double(n_batch)/size(x,1);


		  corrected_idx=[];
		  for j=1:C
			  for i=idx
				  assert(i<=n_batch && i>=1)
				  corrected_idx=[corrected_idx, i+(j-1)*n_batch];
			  end
          end

		  if hyp.stochastic_approx==1
			  %gf C-by-n
			  %gv C-by-C-by-n
			  [gf, gv] = sampling_m_E(y, m_u_batch(corrected_idx,:), V_u_batch(corrected_idx,corrected_idx), lik_name, 'random', hyp.sample_size);
          else
			  error('do not support')
          end

		  %using unbaised weight
		  gf = gf .* weight;
		  gv = gv .* weight;

		  %mapping the change in a mini_batch to the change in the whole batch 
		  df_batch=zeros(C,n_batch);
		  df_batch(:,idx)=gf;

		  dv_batch=zeros(C,C,n_batch);
		  dv_batch(:,:,idx)=gv;

		  g_rate=rate/(iter)^(hyp.power);

		  for j=1:C
			  idx=(j-1)*n_batch+1:j*n_batch;
			  g_m_u_batch=K_batch\(m_u_batch(idx,:)-m_batch)-df_batch(j,:)';

			  if isfield(hyp,'adadelta')
					assert( ~isfield(hyp,'momentum') )
					decay_factor=hyp.decay_factor;
					m_u_scale=decay_factor .* g_acc_m_u_batch(idx,:) + (1.0-decay_factor) .* (g_m_u_batch.^2);
					g_acc_m_u_batch(idx,:)=m_u_scale;
					g_m_u_batch = (hyp.learning_rate .* g_m_u_batch .* sqrt(g_delta_acc_m_u_batch(idx,:) + epsilon) ./ sqrt(m_u_scale+epsilon) );
					g_delta_acc_m_u_batch(idx,:)=decay_factor .* g_delta_acc_m_u_batch(idx,:) + (1.0-decay_factor) .* (g_m_u_batch.^2);
					m_u_batch(idx,:)=m_u_batch(idx,:)-g_m_u_batch;
			  end

			  if isfield(hyp,'momentum')
				  assert( ~isfield(hyp,'adadelta') )
				  momentum_m_u_batch(idx,:)=hyp.momentum*momentum_m_u_batch(idx,:)-g_rate*g_m_u_batch;
				  m_u_batch(idx,:)=m_u_batch(idx,:)+momentum_m_u_batch(idx,:);
		      end
          end

		  if hyp.stochastic_approx==1
			  %gf C-by-n
			  %gv C-by-C-by-n
			  [df_batch, dv_batch] = sampling_m_E(y_batch, m_u_batch, V_u_batch, lik_name, 'random', hyp.sample_size);
          else
			  error('do not support')
          end

		  W_batch = -2 .* convert_C(dv_batch);
		  %g_V_u_batch=0.5 .* ( K_C_batch\eye(n_batch*C) + W_batch - V_u_batch\eye(n_batch*C) );

		  [L_batch D_batch]=ldl(W_batch);
		  sd_batch=sqrt(abs(diag(D_batch))).* sign(diag(D_batch));
		  L_batch=L_batch*diag(sd_batch);
		  R_batch=chol(L_batch'*K_C_batch*L_batch + eye(n_batch*C));%R'*R==L'*K_C*L + diag(inv_d)
		  T_batch = R_batch'\(L_batch'*K_C_batch);
		  A_batch=K_C_batch-T_batch'*T_batch;%A=inv(inv(K_C)+W)
		  g_V_u_batch=0.5 .* ( A_batch\eye(n_batch*C) - V_u_batch\eye(n_batch*C) );

		  %tmp_batch=A_batch-A_batch*((A_batch-V_u_batch)\A_batch); %tmp= inv( inv(A) - inv(V_u) )
		  %tmp_batch=-V_u_batch-V_u_batch*((A_batch-V_u_batch)\V_u_batch);%tmp= inv( inv(A) - inv(V_u) )
		  %g_V_u_batch= 0.5.*( tmp_batch\eye(C*n_batch) );%g_V_u=0.5.*(inv(K_C)+ W - V_u\eye(C*n) ) 

		  if isfield(hyp,'momentum')
			  assert( ~isfield(hyp,'adadelta') )
			  momentum_V_u_batch=hyp.momentum*momentum_V_u_batch-g_rate*g_V_u_batch;
			  V_u_batch=V_u_batch+momentum_V_u_batch;
		  end
		  if isfield(hyp,'adadelta')
			  assert( ~isfield(hyp,'momentum') )
			  decay_factor=hyp.decay_factor;
			  V_u_scale=decay_factor .* g_acc_V_u_batch + (1.0-decay_factor) .* (g_V_u_batch.^2);
			  g_acc_V_u_batch=V_u_scale;
			  g_V_u_batch = (hyp.learning_rate .* g_V_u_batch .* sqrt(g_delta_acc_V_u_batch + epsilon) ./ sqrt(V_u_scale+epsilon) );
			  g_delta_acc_V_u_batch=decay_factor .* g_delta_acc_V_u_batch + (1.0-decay_factor) .* (g_V_u_batch.^2);
			  V_u_batch=V_u_batch-g_V_u_batch;
		  end
		  
		  V_u_batch= V_u_batch - diag( diag(V_u_batch) ) + diag( abs(diag(V_u_batch)) );
		  chol(V_u_batch);


		%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

		debug_count=debug_count+size(x,1);
		mini_batch_counter=mini_batch_counter+1;
	end
	assert(debug_count==n_batch);

	%{
	m_u_batch=zeros(C*n_batch,1);
	for j=1:C
		idx=(j-1)*n_batch+1:j*n_batch;
		m_u_batch(idx,:)=K_batch*alpha_u_batch(idx,:)+m_batch;
	end
	%}

	if hyp.stochastic_approx==1
		%df C-by-n
		%dv C-by-C-by-n
		[df, dv] = sampling_m_E(y_batch, m_u_batch, V_u_batch, lik_name, 'random', hyp.sample_size);
	else
		error('do not support')
	end
	W_batch = -2 .* dv;
	W_C=convert_C(W_batch);
	[L D]=ldl(W_C);
	%sd=sqrt(abs(diag(D))).*sign(diag(D));
	sd=sqrt(abs(diag(D)));
	L_batch=L*diag(sd);
	R_batch=chol(L_batch'*K_C_batch*L_batch + eye(n_batch*C));%R'*R==L'*K_C*L + diag(inv_d)
	T_batch = R_batch'\(L_batch'*K_C_batch);
	nlZ_batch=batch_nlz_m_full(lik_name, hyp, K_batch, m_batch, m_u_batch, V_u_batch, y_batch, R_batch,L_batch,T_batch);
	pass=pass+1;
	fprintf('pass:%d) %.4f\n', pass, nlZ_batch);



	if hyp.is_save==1
		global cache_post;
		global cache_nlz;
		alpha_batch=zeros(C*n_batch,1);
		for j=1:C
			idx=(j-1)*n_batch+1:j*n_batch;
			alpha_batch(idx,:)=K_batch\(m_u_batch(idx,:)-m_batch);
		end
		post.sW = R_batch;                                             % return argument
		post.alpha = alpha_batch;
		post.L = L_batch;                                              % L'*L=B=eye(n)+sW*K*sW
		cache_post=[cache_post; post];
		cache_nlz=[cache_nlz; nlZ_batch];
	 end
end


alpha_batch=zeros(C*n_batch,1);
for j=1:C
	idx=(j-1)*n_batch+1:j*n_batch;
	alpha_batch(idx,:)=K_batch\(m_u_batch(idx,:)-m_batch);
end
post.sW = R_batch;                                             % return argument
post.alpha = alpha_batch;
post.L = L_batch;        
T_batch = R_batch'\(L_batch'*K_C_batch);
nlZ=batch_nlz_m_full(lik_name, hyp, K_batch, m_batch, m_u_batch, V_u_batch, y_batch, R_batch, L_batch, T_batch);
fprintf('final: %.4f\n', nlZ);

if nargout>2
  warning('to be implemented\n');
  dnlZ = NaN;
end

function tW_C = convert_C(tW)
	C=size(tW,1);
	n=size(tW,3);
	C_n=C*n;
	tW_C=zeros(C_n,C_n);
	for i=1:n
	  idx=i:n:C_n;
	  tW_C(idx,idx)=tW(:,:,i);
	end
